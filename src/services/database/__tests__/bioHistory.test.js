import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({ execute: vi.fn(), executeNonQuery: vi.fn() }));
vi.mock('../../sqlite.js', () => ({ default: mocks }));
vi.mock('../index.js', () => ({ dbVars: { userPrefix: 'usr_me' } }));

import { feed } from '../feed.js';

describe('friend bio history database', () => {
    beforeEach(() => {
        mocks.execute.mockReset();
        mocks.executeNonQuery.mockReset();
    });

    it('pages by user ID and timestamp plus row ID without losing tied timestamps', async () => {
        mocks.execute.mockImplementation(async (callback) => {
            callback([9, '2026-09-23T10:00:00.000Z', 'C', 'B']);
            callback([8, '2026-09-23T10:00:00.000Z', 'B', 'A']);
        });
        const result = await feed.getFriendBioHistory('usr_friend', {
            cursor: { createdAt: '2026-09-24T00:00:00.000Z', id: 11 },
            limit: 1
        });

        expect(result.rows).toEqual([{ id: 9, observedAt: '2026-09-23T10:00:00.000Z', bio: 'C', previousBio: 'B' }]);
        expect(result.nextCursor).toEqual({ createdAt: '2026-09-23T10:00:00.000Z', id: 9 });
        expect(mocks.execute.mock.calls[0][1]).toContain('usr_me_feed_bio');
        expect(mocks.execute.mock.calls[0][1]).toContain('user_id = @userId');
        expect(mocks.execute.mock.calls[0][1]).toContain('id < @cursorId');
        expect(mocks.execute.mock.calls[0][2]['@limit']).toBe(2);
    });

    it('counts only actual changes for one user', async () => {
        mocks.execute.mockImplementation(async (callback) => callback([7]));
        expect(await feed.getFriendBioHistoryCount('usr_friend')).toBe(7);
        expect(mocks.execute.mock.calls[0][1]).toContain('bio <> previous_bio');
        expect(mocks.execute.mock.calls[0][2]).toEqual({ '@userId': 'usr_friend' });
    });

    it('avoids writing the same observed change twice', () => {
        feed.addBioToDatabase({
            created_at: '2026-09-23T10:00:00.000Z',
            userId: 'usr_friend',
            displayName: 'Friend',
            bio: 'B',
            previousBio: 'A'
        });
        expect(mocks.executeNonQuery.mock.calls[0][0]).toContain('WHERE NOT EXISTS');
        expect(mocks.executeNonQuery.mock.calls[0][0]).toContain('user_id = @user_id');
    });
});
