import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useAdminAuth } from "../../features/auth";
import { useAdminUsers } from "../../features/users";
import { UsersPage } from "./UsersPage";

vi.mock("../../features/auth", () => ({
  useAdminAuth: vi.fn(),
}));

vi.mock("../../features/users/hooks/useAdminUsers", () => ({
  useAdminUsers: vi.fn(),
}));

const accounts = [
  {
    id: 1,
    email: "john.doe@example.com",
    firstName: "John",
    lastName: "Doe",
    displayName: "John Doe",
    createdDate: "2024-01-10",
    active: true,
    accountType: "ADMIN" as const,
  },
  {
    id: 10,
    email: "riley.reader@example.com",
    firstName: "Riley",
    lastName: "Reader",
    displayName: "Riley Reader",
    createdDate: "2024-06-06",
    active: true,
    accountType: "USER" as const,
  },
  {
    id: 3,
    email: "bob.johnson@example.com",
    firstName: "Bob",
    lastName: "Johnson",
    displayName: "Bob Johnson",
    createdDate: "2024-03-20",
    active: false,
    accountType: "USER" as const,
  },
];

describe("UsersPage", () => {
  const update = vi.fn();
  const load = vi.fn();
  const logout = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useAdminAuth).mockReturnValue({
      status: "authenticated",
      identity: { email: "john.doe@example.com", name: "John Doe" },
      error: null,
      logout,
    });
    vi.mocked(useAdminUsers).mockReturnValue({
      users: accounts,
      error: null,
      savingUserIds: new Set(),
      load,
      update,
    });
    update.mockImplementation(async (userId, changes) => ({
      ...accounts.find((account) => account.id === userId)!,
      ...changes,
    }));
  });

  it("shows account totals and combines text, type and activity filters", async () => {
    const user = userEvent.setup();
    render(<UsersPage />);

    expect(screen.getByText("Showing 3 of 3 accounts")).toBeInTheDocument();
    expect(screen.getByLabelText("Account totals")).toHaveTextContent(
      /All\s*3\s*Official\s*0\s*Admins\s*1\s*Inactive\s*1/,
    );
    expect(screen.getByText("Riley Reader")).toBeInTheDocument();
    expect(screen.getByText("Bob Johnson")).toBeInTheDocument();
    expect(screen.getByText("riley.reader@example.com")).toBeInTheDocument();

    await user.type(screen.getByLabelText("Find an account"), "riley");

    expect(screen.getByText("Showing 1 of 3 accounts")).toBeInTheDocument();
    expect(screen.getByText("Riley Reader")).toBeInTheDocument();
    expect(screen.queryByText("Bob Johnson")).not.toBeInTheDocument();

    await user.clear(screen.getByLabelText("Find an account"));
    await user.selectOptions(screen.getByLabelText("Type"), "USER");
    expect(screen.getByText("Showing 2 of 3 accounts")).toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText("Status"), "INACTIVE");
    expect(screen.getByText("Showing 1 of 3 accounts")).toBeInTheDocument();
    expect(screen.getByText("Bob Johnson")).toBeInTheDocument();
    expect(screen.queryByText("Riley Reader")).not.toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText("Type"), "ADMIN");
    expect(screen.getByText("No accounts match these filters.")).toBeInTheDocument();
  });

  it("lets an admin promote and deactivate an account before saving", async () => {
    const user = userEvent.setup();
    render(<UsersPage />);

    await user.selectOptions(screen.getByLabelText("Account type for Riley Reader"), "ADMIN");
    await user.click(screen.getByLabelText("Account active for Riley Reader"));
    await user.click(screen.getAllByRole("button", { name: "Save changes" })[0]);

    expect(update).toHaveBeenCalledWith(10, {
      accountType: "ADMIN",
      active: false,
    });
  });

  it("shows a restricted state when the database account is not an active admin", () => {
    vi.mocked(useAdminUsers).mockReturnValue({
      users: null,
      error: { status: 403, message: "You are not allowed to perform this action." },
      savingUserIds: new Set(),
      load,
      update,
    });

    render(<UsersPage />);

    expect(screen.getByRole("heading", { name: "This desk is for active admins." })).toBeInTheDocument();
    expect(screen.queryByText("Accounts desk")).not.toBeInTheDocument();
  });
});
