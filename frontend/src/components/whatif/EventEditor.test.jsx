import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it } from "vitest";
import { createFinancialEvent } from "../../simulation/financialEvents";
import { EventEditor } from "./EventEditor";

function StatefulEditor({ initialEvents = [createFinancialEvent("ONE_TIME_EXPENSE", "event-1")] }) {
  const [events, setEvents] = useState(initialEvents);
  return <EventEditor events={events} onChange={setEvents} errors={{}} disabled={false} />;
}

describe("EventEditor", () => {
  it("shows only the fields contracted for the selected event type", async () => {
    const user = userEvent.setup();
    render(<StatefulEditor />);

    expect(screen.getByLabelText("적용 월")).toBeInTheDocument();
    expect(screen.getByLabelText("지출 금액")).toBeInTheDocument();
    expect(screen.queryByLabelText("종료 월 · 포함")).not.toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText("이벤트 유형"), "INCOME_PAUSE");
    expect(screen.getByLabelText("시작 월")).toBeInTheDocument();
    expect(screen.getByLabelText("종료 월 · 포함")).toBeInTheDocument();
    expect(screen.queryByLabelText(/금액/)).not.toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText("이벤트 유형"), "EXTRA_DEBT_REPAYMENT");
    expect(screen.getByLabelText("적용 월")).toBeInTheDocument();
    expect(screen.getByLabelText("추가 상환액")).toBeInTheDocument();
  });

  it("adds and deletes events, and disables adding at twenty", async () => {
    const user = userEvent.setup();
    const { rerender } = render(<StatefulEditor />);
    await user.click(screen.getByRole("button", { name: "+ 이벤트 추가" }));
    expect(screen.getByText("2 / 20")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "이벤트 2 삭제" }));
    expect(screen.getByText("1 / 20")).toBeInTheDocument();

    const twenty = Array.from({ length: 20 }, (_, index) => createFinancialEvent("ONE_TIME_EXPENSE", `event-${index}`));
    rerender(<EventEditor events={twenty} onChange={() => {}} errors={{}} disabled={false} />);
    expect(screen.getByRole("button", { name: "+ 이벤트 추가" })).toBeDisabled();
  });
});
