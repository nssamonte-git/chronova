/** Account deletion, independent of AWS so the ordering can be unit-tested. */

export interface AccountData {
  /** Removes every stored item of the user; returns how many were deleted. */
  deleteAllFor(userId: string): Promise<number>;
}

export interface IdentityAdmin {
  /** Deletes the sign-in identity, so the user can no longer obtain tokens. */
  deleteUser(username: string): Promise<void>;
}

/**
 * Data first, then the identity: if deleting the data fails the user can still
 * sign in and retry; the reverse order could leave data nobody can delete.
 */
export async function deleteAccount(data: AccountData, identity: IdentityAdmin, userId: string, username: string) {
  const deleted = await data.deleteAllFor(userId);
  await identity.deleteUser(username);
  return { deleted };
}
