/**
 * Reconstruct visits from location changes observed by VRCX. The final visit
 * stays open when no later event establishes an exit time.
 *
 * @param {{
 *     created_at: string;
 *     type: string;
 *     location: string;
 *     worldName?: string;
 *     previousLocation?: string;
 *     time?: number;
 * }[]} events
 */
export function buildFriendWorldVisits(events) {
    const worlds = new Map();
    let current = null;

    function addVisit(location, worldName, enteredAt, observedAt, durationMs = null) {
        const worldId = /^wrld_[^:]+/.exec(location)?.[0];
        if (!worldId) {
            return null;
        }
        let world = worlds.get(worldId);
        if (!world) {
            world = { worldId, worldName: worldName || worldId, visitCount: 0, lastVisited: observedAt, visits: [] };
            worlds.set(worldId, world);
        } else if (worldName) {
            world.worldName = worldName;
        }
        const visit = { location, enteredAt, exitedAt: null, durationMs };
        world.visits.push(visit);
        world.visitCount++;
        if (Date.parse(observedAt) > Date.parse(world.lastVisited)) {
            world.lastVisited = observedAt;
        }
        return visit;
    }

    const ordered = events
        .map((event, index) => ({ event, index, timestamp: Date.parse(event.created_at) }))
        .filter(({ timestamp }) => Number.isFinite(timestamp))
        .sort((left, right) => left.timestamp - right.timestamp || left.index - right.index);

    for (const { event, timestamp } of ordered) {
        if (event.type !== 'Online' && event.type !== 'Offline' && event.type !== 'GPS') {
            continue;
        }

        const location = typeof event.location === 'string' ? event.location : '';
        if (event.type === 'GPS' && current?.location === location) {
            continue;
        }

        const previousLocation = event.type === 'GPS' ? event.previousLocation : location;
        const reportedDuration = Number.isFinite(event.time) && event.time >= 0 ? event.time : null;
        if (current && event.type !== 'Online' && previousLocation === current.location) {
            const elapsed = timestamp - Date.parse(current.enteredAt);
            // GPS time excludes travel. A large discrepancy instead suggests a gap in observation.
            if (reportedDuration !== null && reportedDuration <= elapsed && elapsed - reportedDuration <= 30 * 60_000) {
                current.durationMs = reportedDuration;
                current.exitedAt = new Date(Date.parse(current.enteredAt) + reportedDuration).toISOString();
            } else if (reportedDuration === null && elapsed >= 0) {
                current.durationMs = elapsed;
                current.exitedAt = event.created_at;
            }
        } else if (event.type === 'GPS' && previousLocation && previousLocation !== current?.location) {
            // The first GPS event after startup may be the only evidence of the previous world.
            addVisit(previousLocation, '', null, event.created_at, reportedDuration);
        }
        current = null;

        if (event.type === 'Offline') {
            continue;
        }

        current = addVisit(location, event.worldName, event.created_at, event.created_at);
    }

    return [...worlds.values()]
        .map((world) => ({ ...world, visits: world.visits.reverse() }))
        .sort((left, right) => Date.parse(right.lastVisited) - Date.parse(left.lastVisited));
}
