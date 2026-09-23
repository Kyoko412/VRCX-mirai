import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({ executeNonQuery: vi.fn() }));
vi.mock('../../sqlite.js', () => ({ default: mocks }));
vi.mock('../index.js', () => ({ dbVars: { userPrefix: 'usr_me' } }));

import { gameLog } from '../gameLog.js';
import { ownerForLiveGameLocation } from '../../mobileGameOwnership.js';

const entry = {
    created_at: '2026-09-24T02:00:00.000Z',
    location: 'wrld_a:123',
    worldId: 'wrld_a',
    worldName: 'World A',
    time: null,
    groupName: ''
};

describe('mobile game location ownership', () => {
    beforeEach(() => {
        mocks.executeNonQuery.mockReset();
    });

    it('tags a newly inserted live location with the event-time account', async () => {
        mocks.executeNonQuery.mockResolvedValueOnce(1).mockResolvedValueOnce(1);
        await gameLog.addGamelogLocationToDatabase(entry, 'usr_one');
        expect(mocks.executeNonQuery).toHaveBeenCalledTimes(2);
        expect(mocks.executeNonQuery.mock.calls[1][0]).toContain('mobile_game_location_owner_v1');
        expect(mocks.executeNonQuery.mock.calls[1][1]['@ownerUserId']).toBe('usr_one');
    });

    it('does not claim a replay, logged-out event, or a duplicate row', async () => {
        expect(ownerForLiveGameLocation({ trackEncounter: false, isLoggedIn: true, accountId: 'usr_one' })).toBeNull();
        expect(ownerForLiveGameLocation({ trackEncounter: true, isLoggedIn: false, accountId: 'usr_one' })).toBeNull();
        mocks.executeNonQuery.mockResolvedValueOnce(0);
        await gameLog.addGamelogLocationToDatabase(entry, 'usr_two');
        expect(mocks.executeNonQuery).toHaveBeenCalledTimes(1);
    });

    it('uses the primary key and INSERT OR IGNORE to prevent another account claiming a row', async () => {
        mocks.executeNonQuery.mockResolvedValueOnce(1).mockResolvedValueOnce(1);
        await gameLog.addGamelogLocationToDatabase(entry, 'usr_one');
        expect(mocks.executeNonQuery.mock.calls[1][0]).toContain('INSERT OR IGNORE INTO mobile_game_location_owner_v1');
        expect(ownerForLiveGameLocation({ trackEncounter: true, isLoggedIn: true, accountId: 'usr_two' })).toBe(
            'usr_two'
        );
    });
});
