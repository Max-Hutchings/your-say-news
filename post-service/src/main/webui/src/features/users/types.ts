export type AccountType = "USER" | "OFFICIAL" | "ADMIN";

export type AdminUser = {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  displayName: string;
  createdDate: string;
  active: boolean;
  accountType: AccountType;
};

export type AdminUserUpdate = Pick<AdminUser, "accountType" | "active">;
