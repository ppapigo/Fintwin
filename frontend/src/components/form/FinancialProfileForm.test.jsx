import { act, fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { FinancialProfileForm } from "./FinancialProfileForm";

const LABELS = [
  "월 소득",
  "월 고정지출",
  "월 변동지출",
  "월 저축액",
  "월 투자액",
  "현금성 자산",
  "예금",
  "투자자산",
  "총 대출잔액",
  "대출금리",
];

async function fillValidForm(user) {
  for (const label of LABELS) {
    await user.type(screen.getByLabelText(new RegExp(label)), label === "대출금리" ? "3.1250" : "1000.10");
  }
}

describe("FinancialProfileForm", () => {
  it("rejects every missing required value before an API call", async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn();
    render(<FinancialProfileForm initialValues={{}} onSubmit={onSubmit} submitLabel="저장" />);

    await user.click(screen.getByRole("button", { name: "저장" }));

    expect(screen.getAllByText("필수 입력값입니다.")).toHaveLength(10);
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("rejects negative money and a rate over 100", async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn();
    render(<FinancialProfileForm initialValues={{}} onSubmit={onSubmit} submitLabel="저장" />);
    await fillValidForm(user);
    await user.clear(screen.getByLabelText(/월 소득/));
    await user.type(screen.getByLabelText(/월 소득/), "-1");
    await user.clear(screen.getByLabelText(/대출금리/));
    await user.type(screen.getByLabelText(/대출금리/), "100.0001");

    await user.click(screen.getByRole("button", { name: "저장" }));

    expect(screen.getByText("0 이상의 금액을 소수점 둘째 자리까지 입력해주세요.")).toBeInTheDocument();
    expect(screen.getByText("0 이상 100 이하, 소수점 넷째 자리까지 입력해주세요.")).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("submits exact decimal strings and maps backend field validation", async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn().mockResolvedValue({
      fieldErrors: { monthlyIncome: "서버가 월 소득 값을 거부했습니다." },
      formError: "서버 검증을 통과하지 못했습니다.",
    });
    render(<FinancialProfileForm initialValues={{}} onSubmit={onSubmit} submitLabel="저장" />);
    await fillValidForm(user);

    await user.click(screen.getByRole("button", { name: "저장" }));

    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({
      monthlyIncome: "1000.10",
      loanInterestRate: "3.1250",
    }));
    expect(await screen.findByText("서버가 월 소득 값을 거부했습니다.")).toBeInTheDocument();
  });

  it("prevents duplicate submission while the first request is pending", async () => {
    const user = userEvent.setup();
    let finish;
    const onSubmit = vi.fn(() => new Promise((resolve) => { finish = resolve; }));
    render(<FinancialProfileForm initialValues={{}} onSubmit={onSubmit} submitLabel="저장" />);
    await fillValidForm(user);
    const form = screen.getByRole("button", { name: "저장" }).closest("form");

    fireEvent.submit(form);
    fireEvent.submit(form);

    expect(onSubmit).toHaveBeenCalledTimes(1);
    await act(async () => finish(null));
  });
});
