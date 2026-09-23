import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
    upsertMutualEncounter: vi.fn(),
    setMutualEncounterDecision: vi.fn(),
    getMutualEncounterSummaries: vi.fn(),
    getMutualCounts: vi.fn(),
    currentUser: { hasSharedConnectionsOptOut: false }
}));

vi.mock('../../services/database', () => ({
    database: {
        upsertMutualEncounter: mocks.upsertMutualEncounter,
        setMutualEncounterDecision: mocks.setMutualEncounterDecision,
        getMutualEncounterSummaries: mocks.getMutualEncounterSummaries
    },
    dbVars: { userId: 'usr_me' }
}));
vi.mock('../../api', () => ({ userRequest: { getMutualCounts: mocks.getMutualCounts } }));
vi.mock('../user', () => ({ useUserStore: () => ({ currentUser: mocks.currentUser }) }));

import { useMutualEncountersStore } from '../mutualEncounters.js';

describe('mutual encounters store', () => {
    beforeEach(() => {
        setActivePinia(createPinia());
        mocks.currentUser.hasSharedConnectionsOptOut = false;
        mocks.upsertMutualEncounter.mockReset().mockResolvedValue({ status: 'unknown' });
        mocks.setMutualEncounterDecision.mockReset().mockResolvedValue(undefined);
        mocks.getMutualEncounterSummaries
            .mockReset()
            .mockResolvedValue(new Map([['usr_other', { qualifiedCount: 1, unknownCount: 0 }]]));
        mocks.getMutualCounts.mockReset().mockResolvedValue({ json: { friends: 2 } });
    });

    it('checks mutual friends and refreshes the confirmed total', async () => {
        const store = useMutualEncountersStore();
        store.enterVisit({ accountId: 'usr_me', location: 'wrld_alpha:1', enteredAt: '2026-09-23T10:00:00.000Z' });
        await store.playerJoined({ userId: 'usr_other', displayName: 'Other' });
        await vi.waitFor(() => expect(store.summaries.get('usr_other')?.qualifiedCount).toBe(1));

        expect(mocks.getMutualCounts).toHaveBeenCalledWith({ userId: 'usr_other' });
        expect(store.currentStatuses.get('usr_other')).toEqual({ status: 'qualified', mutualFriendCount: 2 });
    });

    it('keeps the result unknown when current account hides mutual connections', async () => {
        mocks.currentUser.hasSharedConnectionsOptOut = true;
        const store = useMutualEncountersStore();
        store.enterVisit({ accountId: 'usr_me', location: 'wrld_alpha:1', enteredAt: '2026-09-23T10:00:00.000Z' });
        await store.playerJoined({ userId: 'usr_other', displayName: 'Other' });

        expect(mocks.getMutualCounts).not.toHaveBeenCalled();
        expect(mocks.setMutualEncounterDecision).not.toHaveBeenCalled();
        expect(store.currentStatuses.get('usr_other')?.status).toBe('unknown');
    });

    it('loads historical confirmed counts without an active room visit', async () => {
        const store = useMutualEncountersStore();
        await store.refreshSummaries(['usr_other']);
        expect(store.summaries.get('usr_other')?.qualifiedCount).toBe(1);
    });
});
