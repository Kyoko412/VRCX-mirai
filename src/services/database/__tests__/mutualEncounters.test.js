import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({ execute: vi.fn(), executeNonQuery: vi.fn() }));

vi.mock('../../sqlite.js', () => ({ default: mocks }));
vi.mock('../index.js', () => ({ dbVars: { userPrefix: 'usr_test' } }));

import { mutualEncounters } from '../mutualEncounters.js';

describe('mutual encounter database', () => {
    beforeEach(() => {
        mocks.execute.mockReset();
        mocks.executeNonQuery.mockReset();
    });

    it('keeps one decision per room visit and player, without overwriting a verified result', async () => {
        mocks.execute.mockImplementation(async (callback) => callback(['qualified', 2, '2026-09-23T10:00:00.000Z']));
        const candidate = {
            visitKey: 'usr_me:2026-09-23T09:55:00.000Z:wrld_a:1',
            userId: 'usr_other',
            location: 'wrld_a:1',
            observedAt: '2026-09-23T10:00:00.000Z',
            displayName: 'Other',
            worldName: 'World A'
        };

        const prior = await mutualEncounters.upsertMutualEncounter(candidate);
        await mutualEncounters.setMutualEncounterDecision(
            candidate.visitKey,
            candidate.userId,
            'not_qualified',
            0,
            '2026-09-23T10:01:00.000Z'
        );

        expect(prior).toEqual({ status: 'qualified', mutualFriendCount: 2, checkedAt: '2026-09-23T10:00:00.000Z' });
        expect(mocks.executeNonQuery.mock.calls[0][0]).toContain('INSERT OR IGNORE INTO usr_test_mutual_encounters_v1');
        expect(mocks.executeNonQuery.mock.calls[1][0]).toContain(
            "WHERE visit_key = @visitKey AND other_user_id = @userId AND status = 'unknown'"
        );
    });

    it('counts only confirmed positive visits and returns their history', async () => {
        mocks.execute.mockImplementation(async (callback, sql) => {
            if (sql.includes('GROUP BY other_user_id')) callback(['usr_other', 2, 1]);
            else callback(['2026-09-23T10:00:00.000Z', 'wrld_a:1', 'World A', 2]);
        });

        const summary = await mutualEncounters.getMutualEncounterSummaries(['usr_other']);
        const history = await mutualEncounters.getMutualEncounterHistory('usr_other');

        expect(summary.get('usr_other')).toEqual({ qualifiedCount: 2, unknownCount: 1 });
        expect(history).toEqual([
            {
                observedAt: '2026-09-23T10:00:00.000Z',
                location: 'wrld_a:1',
                worldName: 'World A',
                mutualFriendCount: 2
            }
        ]);
        expect(mocks.execute.mock.calls[0][1]).toContain("SUM(CASE WHEN status = 'qualified' THEN 1 ELSE 0 END)");
        expect(mocks.execute.mock.calls[1][1]).toContain("status = 'qualified'");
    });
});
