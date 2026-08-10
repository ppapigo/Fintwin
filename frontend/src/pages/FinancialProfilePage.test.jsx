import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../api/apiClient";
import {
  createProfile,
  getCurrentProfile,
  PROFILE_FIELDS,
  updateProfile,
} from "../api/financialProfileApi";
import { FinancialProfilePage } from "./FinancialProfilePage";

vi.mock("../api/financialProfileApi", async (importOriginal) => {
  const original = await importOriginal();
  return {
    ...original,
    getCurrentProfile: vi.fn(),
    createProfile: vi.fn(),
    updateProfile: vi.fn(),
  };
});

const PROFILE = {
  ...Object.fromEntries(PROFILE_FIELDS.map((field) => [field, field === "loanInterestRate" ? "2.7500" : "500.25"])),
  version: 2,
  createdAt: "2026-08-10T10:00:00",
};

function renderPage(mode) {
  return render(
    <MemoryRouter initialEntries={[mode === "create" ? "/profile/setup" : "/profile/edit"]}>
      <Routes>
        <Route path="/profile/setup" element={<FinancialProfilePage mode="create" />} />
        <Route path="/profile/edit" element={<FinancialProfilePage mode="edit" />} />
        <Route path="/profile/summary" element={<div>profile saved</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

async function fillWithZero(user) {
  const labels = ["월 소득", "월 고정지출", "월 변동지출", "월 저축액", "월 투자액", "현금성 자산", "예금", "투자자산", "총 대출잔액", "대출금리"];
  for (const label of labels) await user.type(screen.getByLabelText(new RegExp(label)), "0");
}

describe("FinancialProfilePage", () => {
  beforeEach(() => {
    vi.mocked(getCurrentProfile).mockReset();
    vi.mocked(createProfile).mockReset();
    vi.mocked(updateProfile).mockReset();
  });

  it("shows onboarding for a missing profile and creates it", async () => {
    const user = userEvent.setup();
    vi.mocked(getCurrentProfile).mockResolvedValue(null);
    vi.mocked(createProfile).mockResolvedValue({ ...PROFILE, version: 1 });
    renderPage("create");

    expect(await screen.findByText("첫 Financial Twin을 위한 기준점")).toBeInTheDocument();
    await fillWithZero(user);
    await user.click(screen.getByRole("button", { name: "Financial Profile 만들기" }));

    expect(await screen.findByText("profile saved")).toBeInTheDocument();
    expect(createProfile).toHaveBeenCalledWith(Object.fromEntries(PROFILE_FIELDS.map((field) => [field, "0"])));
  });

  it("loads all current values and submits a full immutable update", async () => {
    const user = userEvent.setup();
    vi.mocked(getCurrentProfile).mockResolvedValue(PROFILE);
    vi.mocked(updateProfile).mockResolvedValue({ ...PROFILE, version: 3 });
    renderPage("edit");

    expect(await screen.findByDisplayValue("2.7500")).toBeInTheDocument();
    expect(screen.getAllByDisplayValue("500.25")).toHaveLength(9);
    await user.click(screen.getByRole("button", { name: "새 버전으로 저장" }));

    expect(await screen.findByText("profile saved")).toBeInTheDocument();
    expect(updateProfile).toHaveBeenCalledWith(expect.objectContaining(PROFILE_FIELDS.reduce((result, field) => {
      result[field] = PROFILE[field];
      return result;
    }, {})));
  });

  it("shows a refresh action for a version conflict", async () => {
    const user = userEvent.setup();
    vi.mocked(getCurrentProfile).mockResolvedValue(PROFILE);
    vi.mocked(updateProfile).mockRejectedValue(new ApiError("conflict", { status: 409, code: "RESOURCE_CONFLICT" }));
    renderPage("edit");
    await screen.findByDisplayValue("2.7500");

    await user.click(screen.getByRole("button", { name: "새 버전으로 저장" }));

    expect(await screen.findByRole("button", { name: "최신 값 다시 불러오기" })).toBeInTheDocument();
  });
});
