import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({ execute: vi.fn() }));

vi.mock('../../sqlite.js', () => ({ default: { execute: mocks.execute } }));
vi.mock('../index.js', () => ({ dbVars: { userPrefix: 'usr_test' } }));

import { feed } from '../feed.js';

describe('feed.getFriendWorldVisitEvents', () => {
    beforeEach(() => {
        mocks.execute.mockReset();
    });

    it('reads only the selected friend’s position and presence events in time order', async () => {
        mocks.execute.mockImplementation(async (callback) => {
            callback(['2026-09-01T10:00:00.000Z', 'Online', 'wrld_alpha:1', 'Alpha', null, null]);
            callback(['2026-09-01T10:10:00.000Z', 'GPS', 'wrld_beta:2', 'Beta', 'wrld_alpha:1', 600000]);
        });

        const events = await feed.getFriendWorldVisitEvents('usr_friend', '2026-06-01T00:00:00.000Z');

        expect(events).toEqual([
            {
                created_at: '2026-09-01T10:00:00.000Z',
                type: 'Online',
                location: 'wrld_alpha:1',
                worldName: 'Alpha',
                previousLocation: null,
                time: null
            },
            {
                created_at: '2026-09-01T10:10:00.000Z',
                type: 'GPS',
                location: 'wrld_beta:2',
                worldName: 'Beta',
                previousLocation: 'wrld_alpha:1',
                time: 600000
            }
        ]);
        const [, sql, params] = mocks.execute.mock.calls[0];
        expect(sql).toContain('usr_test_feed_gps');
        expect(sql).toContain('usr_test_feed_online_offline');
        expect(sql).toContain('ORDER BY created_at ASC');
        expect(sql.match(/created_at >= @dateFrom/g)).toHaveLength(2);
        expect(params).toEqual({ '@userId': 'usr_friend', '@dateFrom': '2026-06-01T00:00:00.000Z' });
    });
});
