import { describe, expect, it, vi } from 'vitest';

import { createEncounterTracker } from '../encounterTracker.js';

function createDependencies(overrides = {}) {
    return {
        upsertCandidate: vi.fn().mockResolvedValue({ status: 'unknown' }),
        decide: vi.fn().mockResolvedValue(undefined),
        lookupMutuals: vi.fn().mockResolvedValue(2),
        onChange: vi.fn(),
        wait: vi.fn().mockResolvedValue(undefined),
        now: () => '2026-09-23T10:01:00.000Z',
        ...overrides
    };
}

const visit = (enteredAt = '2026-09-23T10:00:00.000Z', accountId = 'usr_me') => ({
    accountId,
    enteredAt,
    location: 'wrld_alpha:1',
    worldName: 'Alpha'
});

describe('encounter tracker', () => {
    it('counts a player once per self visit, but counts a later visit to the same instance', async () => {
        const deps = createDependencies();
        const tracker = createEncounterTracker(deps);
        tracker.enterVisit(visit());

        await Promise.all([
            tracker.playerJoined({ userId: 'usr_other', displayName: 'Other' }),
            tracker.playerJoined({ userId: 'usr_other', displayName: 'Other' })
        ]);
        tracker.playerLeft('usr_other');
        await tracker.playerJoined({ userId: 'usr_other', displayName: 'Other' });
        expect(deps.lookupMutuals).toHaveBeenCalledTimes(1);
        expect(deps.decide).toHaveBeenCalledTimes(1);

        tracker.enterVisit(visit('2026-09-23T11:00:00.000Z'));
        await tracker.playerJoined({ userId: 'usr_other', displayName: 'Other' });
        expect(deps.lookupMutuals).toHaveBeenCalledTimes(2);
        expect(deps.decide.mock.calls[0][0]).not.toBe(deps.decide.mock.calls[1][0]);
    });

    it('preserves the observed join time in the encounter snapshot', async () => {
        const deps = createDependencies();
        const tracker = createEncounterTracker(deps);
        tracker.enterVisit(visit());
        await tracker.playerJoined({ userId: 'usr_other', observedAt: '2026-09-23T10:00:10.000Z' });
        expect(deps.upsertCandidate.mock.calls[0][0].observedAt).toBe('2026-09-23T10:00:10.000Z');
    });

    it('does not count self, unidentified players, zero mutuals, or a blocked lookup', async () => {
        const deps = createDependencies({
            lookupMutuals: vi.fn().mockResolvedValueOnce(0).mockRejectedValueOnce({ status: 403 })
        });
        const tracker = createEncounterTracker(deps);
        tracker.enterVisit(visit());
        await tracker.playerJoined({ userId: 'usr_me', displayName: 'Me' });
        await tracker.playerJoined({ userId: '', displayName: 'Unknown' });
        await tracker.playerJoined({ userId: 'usr_zero', displayName: 'Zero' });
        await tracker.playerJoined({ userId: 'usr_blocked', displayName: 'Blocked' });

        expect(deps.upsertCandidate).toHaveBeenCalledTimes(2);
        expect(deps.decide).toHaveBeenCalledTimes(1);
        expect(deps.decide.mock.calls[0][2]).toBe('not_qualified');
        expect(deps.onChange).toHaveBeenCalledWith('usr_blocked', { status: 'unknown', mutualFriendCount: null });
    });

    it('ignores a lookup result that arrives after the player leaves or the account changes', async () => {
        let resolveFirst;
        const first = new Promise((resolve) => {
            resolveFirst = resolve;
        });
        const deps = createDependencies({ lookupMutuals: vi.fn().mockReturnValueOnce(first).mockResolvedValue(1) });
        const tracker = createEncounterTracker(deps);
        tracker.enterVisit(visit());
        const pending = tracker.playerJoined({ userId: 'usr_a', displayName: 'A' });
        await vi.waitFor(() => expect(deps.lookupMutuals).toHaveBeenCalledTimes(1));
        tracker.playerLeft('usr_a');
        resolveFirst(3);
        await pending;
        expect(deps.decide).not.toHaveBeenCalled();

        tracker.enterVisit(visit('2026-09-23T11:00:00.000Z', 'usr_another_account'));
        await tracker.playerJoined({ userId: 'usr_a', displayName: 'A' });
        expect(deps.decide).toHaveBeenCalledTimes(1);
        expect(deps.decide.mock.calls[0][0]).toContain('usr_another_account');
    });

    it('does not recheck an already decided visit and retries a rate-limited lookup', async () => {
        const deps = createDependencies({
            upsertCandidate: vi
                .fn()
                .mockResolvedValueOnce({ status: 'qualified', mutualFriendCount: 1 })
                .mockResolvedValue({ status: 'unknown' }),
            lookupMutuals: vi.fn().mockRejectedValueOnce({ status: 429 }).mockResolvedValueOnce(1)
        });
        const tracker = createEncounterTracker(deps);
        tracker.enterVisit(visit());
        await tracker.playerJoined({ userId: 'usr_a', displayName: 'A' });
        await tracker.playerJoined({ userId: 'usr_b', displayName: 'B' });

        expect(deps.lookupMutuals).toHaveBeenCalledTimes(2);
        expect(deps.wait).toHaveBeenCalledTimes(1);
        expect(deps.decide).toHaveBeenCalledTimes(1);
        expect(deps.onChange).toHaveBeenCalledWith('usr_a', { status: 'qualified', mutualFriendCount: 1 });
    });
});
