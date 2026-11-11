import { deleteAccount } from '../lambda/account-core';

describe('deleteAccount', () => {
  it('deletes the data before the identity', async () => {
    const calls: string[] = [];
    const result = await deleteAccount(
      { deleteAllFor: async (user) => (calls.push(`data:${user}`), 3) },
      { deleteUser: async (name) => void calls.push(`user:${name}`) },
      'sub-1',
      'name-1',
    );
    expect(calls).toEqual(['data:sub-1', 'user:name-1']);
    expect(result).toEqual({ deleted: 3 });
  });

  it('keeps the identity when deleting data fails, so the user can retry', async () => {
    const deleteUser = jest.fn();
    await expect(
      deleteAccount({ deleteAllFor: async () => Promise.reject(new Error('throttled')) }, { deleteUser }, 's', 'n'),
    ).rejects.toThrow('throttled');
    expect(deleteUser).not.toHaveBeenCalled();
  });
});
