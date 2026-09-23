/**
 * A visit is one observation of the current user entering an instance.
 * Decisions belong to that visit, not to the reusable instance location.
 */
export function createEncounterTracker({
    upsertCandidate,
    decide,
    lookupMutuals,
    onChange = (_userId, _state) => {},
    now = () => new Date().toISOString(),
    wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms))
}) {
    let current = null;
    let queueTail = Promise.resolve();

    function isCurrent(visit, userId) {
        return current === visit && visit.present.has(userId);
    }

    function enqueue(job) {
        const result = queueTail.then(job, job);
        queueTail = result.catch(() => {});
        return result;
    }

    function enterVisit({ accountId, location, enteredAt, worldName = '' }) {
        if (!accountId || !/^wrld_[^:]+:/.test(location || '') || !Number.isFinite(Date.parse(enteredAt))) {
            current = null;
            return null;
        }
        const visitKey = `${accountId}:${enteredAt}:${location}`;
        if (current?.visitKey === visitKey) return visitKey;
        current = { accountId, location, enteredAt, worldName, visitKey, present: new Set(), seen: new Map() };
        return visitKey;
    }

    async function playerJoined({ userId, displayName = '', observedAt }) {
        const visit = current;
        if (!visit || !userId || userId === visit.accountId) return null;
        visit.present.add(userId);
        if (visit.seen.has(userId)) return visit.seen.get(userId);

        onChange(userId, { status: 'unknown', mutualFriendCount: null });
        const task = (async () => {
            const prior = await upsertCandidate({
                visitKey: visit.visitKey,
                userId,
                location: visit.location,
                observedAt: observedAt && Number.isFinite(Date.parse(observedAt)) ? observedAt : now(),
                displayName,
                worldName: visit.worldName
            });
            if (!isCurrent(visit, userId) || !prior) return null;
            if (prior.status !== 'unknown') {
                onChange(userId, { status: prior.status, mutualFriendCount: prior.mutualFriendCount });
                return prior;
            }
            return enqueue(async () => {
                for (let attempt = 0; attempt < 3; attempt++) {
                    if (!isCurrent(visit, userId)) return null;
                    try {
                        const count = await lookupMutuals(userId);
                        if (!isCurrent(visit, userId) || !Number.isInteger(count) || count < 0) return null;
                        const status = count > 0 ? 'qualified' : 'not_qualified';
                        await decide(visit.visitKey, userId, status, count, now());
                        if (isCurrent(visit, userId)) onChange(userId, { status, mutualFriendCount: count });
                        return { status, mutualFriendCount: count };
                    } catch (error) {
                        if (error?.status !== 429 || attempt === 2) return null;
                        await wait(500 * 2 ** attempt);
                    }
                }
                return null;
            });
        })().catch(() => null);
        visit.seen.set(userId, task);
        return task;
    }

    function playerLeft(userId) {
        current?.present.delete(userId);
    }

    function endVisit() {
        current = null;
    }

    async function restoreVisit(visit, players) {
        if (!enterVisit(visit)) return;
        await Promise.all(players.map((player) => playerJoined(player)));
    }

    return { enterVisit, playerJoined, playerLeft, endVisit, restoreVisit };
}
