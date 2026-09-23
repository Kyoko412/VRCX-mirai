import { describe, expect, it } from 'vitest';

import { buildFriendWorldVisits } from '../buildFriendWorldVisits';

const event = (created_at, type, location, worldName = '', details = {}) => ({
    created_at,
    type,
    location,
    worldName,
    ...details
});

describe('buildFriendWorldVisits', () => {
    it('pairs observed entries with the next move or offline event', () => {
        const worlds = buildFriendWorldVisits([
            event('2026-09-01T10:00:00.000Z', 'Online', 'wrld_alpha:1', 'Alpha'),
            event('2026-09-01T10:10:00.000Z', 'GPS', 'wrld_beta:2', 'Beta', {
                previousLocation: 'wrld_alpha:1',
                time: 10 * 60_000
            }),
            event('2026-09-01T10:45:00.000Z', 'Offline', 'wrld_beta:2')
        ]);

        expect(worlds).toEqual([
            {
                worldId: 'wrld_beta',
                worldName: 'Beta',
                visitCount: 1,
                lastVisited: '2026-09-01T10:10:00.000Z',
                visits: [
                    {
                        location: 'wrld_beta:2',
                        enteredAt: '2026-09-01T10:10:00.000Z',
                        exitedAt: '2026-09-01T10:45:00.000Z',
                        durationMs: 35 * 60_000
                    }
                ]
            },
            {
                worldId: 'wrld_alpha',
                worldName: 'Alpha',
                visitCount: 1,
                lastVisited: '2026-09-01T10:00:00.000Z',
                visits: [
                    {
                        location: 'wrld_alpha:1',
                        enteredAt: '2026-09-01T10:00:00.000Z',
                        exitedAt: '2026-09-01T10:10:00.000Z',
                        durationMs: 10 * 60_000
                    }
                ]
            }
        ]);
    });

    it('counts a new instance of the same world as a second visit but ignores duplicate updates', () => {
        const worlds = buildFriendWorldVisits([
            event('2026-09-01T10:00:00.000Z', 'Online', 'wrld_alpha:1', 'Alpha'),
            event('2026-09-01T10:01:00.000Z', 'GPS', 'wrld_alpha:1', 'Alpha'),
            event('2026-09-01T10:15:00.000Z', 'GPS', 'wrld_alpha:2', 'Alpha', {
                previousLocation: 'wrld_alpha:1',
                time: 15 * 60_000
            })
        ]);

        expect(worlds[0].visitCount).toBe(2);
        expect(worlds[0].visits.map((visit) => visit.durationMs)).toEqual([null, 15 * 60_000]);
        expect(worlds[0].visits[0].exitedAt).toBeNull();
    });

    it('closes a known world when visibility becomes private and does not invent missing exits', () => {
        const worlds = buildFriendWorldVisits([
            event('2026-09-01T10:00:00.000Z', 'Online', 'private'),
            event('2026-09-01T10:05:00.000Z', 'GPS', 'wrld_alpha:1', 'Alpha'),
            event('2026-09-01T10:15:00.000Z', 'GPS', 'private', '', {
                previousLocation: 'wrld_alpha:1',
                time: 10 * 60_000
            }),
            event('2026-09-01T10:20:00.000Z', 'GPS', 'wrld_alpha:3', 'Alpha')
        ]);

        expect(worlds).toHaveLength(1);
        expect(worlds[0].visitCount).toBe(2);
        expect(worlds[0].visits[0].exitedAt).toBeNull();
        expect(worlds[0].visits[1].durationMs).toBe(10 * 60_000);
    });

    it('leaves the exit unknown when a later event follows a gap in observation', () => {
        const worlds = buildFriendWorldVisits([
            event('2026-09-01T10:00:00.000Z', 'Online', 'wrld_alpha:1', 'Alpha'),
            event('2026-09-01T12:00:00.000Z', 'GPS', 'wrld_beta:2', 'Beta', {
                previousLocation: 'wrld_alpha:1',
                time: 5 * 60_000
            })
        ]);

        expect(worlds.find((world) => world.worldId === 'wrld_alpha').visits[0]).toMatchObject({
            exitedAt: null,
            durationMs: null
        });
    });

    it('uses the reported stay when a world change includes travel time', () => {
        const worlds = buildFriendWorldVisits([
            event('2026-09-01T10:00:00.000Z', 'Online', 'wrld_alpha:1', 'Alpha'),
            event('2026-09-01T10:32:00.000Z', 'GPS', 'wrld_beta:2', 'Beta', {
                previousLocation: 'wrld_alpha:1',
                time: 30 * 60_000
            })
        ]);

        expect(worlds.find((world) => world.worldId === 'wrld_alpha').visits[0]).toMatchObject({
            exitedAt: '2026-09-01T10:30:00.000Z',
            durationMs: 30 * 60_000
        });
    });

    it('counts the previous world in a GPS event even without an earlier online event', () => {
        const worlds = buildFriendWorldVisits([
            event('2026-09-01T10:32:00.000Z', 'GPS', 'wrld_beta:2', 'Beta', {
                previousLocation: 'wrld_alpha:1',
                time: 30 * 60_000
            })
        ]);

        expect(worlds.find((world) => world.worldId === 'wrld_alpha')).toMatchObject({
            visitCount: 1,
            visits: [{ location: 'wrld_alpha:1', enteredAt: null, exitedAt: null, durationMs: 30 * 60_000 }]
        });
    });
});
