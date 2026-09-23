import { describe, expect, it, vi } from 'vitest';

import { shouldRestoreObservedVisit, syncObservedEncounterEvent } from '../syncObservedEncounter.js';

describe('observed game-log encounter events', () => {
    it('restores only when the self account still reports the same room', () => {
        expect(shouldRestoreObservedVisit('wrld_a:1', 'wrld_a:1', '')).toBe(true);
        expect(shouldRestoreObservedVisit('wrld_a:1', 'traveling', '')).toBe(false);
        expect(shouldRestoreObservedVisit('wrld_a:1', 'wrld_b:2', '')).toBe(false);
        expect(shouldRestoreObservedVisit('wrld_a:1', 'wrld_a:1', 'wrld_b:2')).toBe(false);
    });
    it('opens a room visit, observes players, and closes it when leaving', async () => {
        const store = { enterVisit: vi.fn(), playerJoined: vi.fn(), playerLeft: vi.fn(), endVisit: vi.fn() };
        const context = {
            store,
            accountId: 'usr_me',
            currentLocation: 'wrld_a:1',
            enteredAt: Date.parse('2026-09-23T10:00:00.000Z')
        };

        syncObservedEncounterEvent(
            { type: 'location', dt: '2026-09-23T10:00:00.000Z', location: 'wrld_a:1', worldName: 'Alpha' },
            context
        );
        syncObservedEncounterEvent(
            { type: 'player-joined', userId: 'usr_other', displayName: 'Other', dt: '2026-09-23T10:00:10.000Z' },
            context
        );
        syncObservedEncounterEvent({ type: 'player-left', userId: 'usr_other' }, context);
        syncObservedEncounterEvent({ type: 'location-destination' }, context);

        expect(store.enterVisit).toHaveBeenCalledWith({
            accountId: 'usr_me',
            location: 'wrld_a:1',
            enteredAt: '2026-09-23T10:00:00.000Z',
            worldName: 'Alpha'
        });
        expect(store.playerJoined).toHaveBeenCalledWith({
            userId: 'usr_other',
            displayName: 'Other',
            observedAt: '2026-09-23T10:00:10.000Z'
        });
        expect(store.playerLeft).toHaveBeenCalledWith('usr_other');
        expect(store.endVisit).toHaveBeenCalledOnce();
    });

    it('does not create an encounter when the current room entry is unknown', () => {
        const store = { enterVisit: vi.fn(), playerJoined: vi.fn() };
        syncObservedEncounterEvent(
            { type: 'player-joined', userId: 'usr_other' },
            {
                store,
                accountId: 'usr_me',
                currentLocation: 'wrld_a:1',
                enteredAt: null
            }
        );
        expect(store.enterVisit).not.toHaveBeenCalled();
        expect(store.playerJoined).not.toHaveBeenCalled();
    });

    it('never tracks a player without a user ID from the log', () => {
        const store = { enterVisit: vi.fn(), playerJoined: vi.fn() };
        syncObservedEncounterEvent(
            { type: 'player-joined', userId: '', displayName: 'Same Name' },
            {
                store,
                accountId: 'usr_me',
                currentLocation: 'wrld_a:1',
                enteredAt: Date.parse('2026-09-23T10:00:00.000Z')
            }
        );
        expect(store.playerJoined).not.toHaveBeenCalled();
    });
});
