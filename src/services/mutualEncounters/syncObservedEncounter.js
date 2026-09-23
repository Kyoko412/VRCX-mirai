/** Forward only observed game events with a known current room to the encounter tracker. */
export function shouldRestoreObservedVisit(roomLocation, selfLocation, travelingToLocation) {
    return /^wrld_[^:]+:/.test(roomLocation || '') && selfLocation === roomLocation && !travelingToLocation;
}

export function syncObservedEncounterEvent(gameLog, { store, accountId, currentLocation, enteredAt, worldName = '' }) {
    if (!accountId) return;
    if (gameLog.type === 'location-destination' || gameLog.type === 'vrc-quit') {
        store.endVisit();
        return;
    }
    if (gameLog.type === 'location') {
        store.enterVisit({
            accountId,
            location: gameLog.location,
            enteredAt: gameLog.dt,
            worldName: gameLog.worldName || ''
        });
        return;
    }
    if (gameLog.type === 'player-left') {
        if (gameLog.userId) store.playerLeft(gameLog.userId);
        return;
    }
    if (
        gameLog.type !== 'player-joined' ||
        !gameLog.userId ||
        !enteredAt ||
        !/^wrld_[^:]+:/.test(currentLocation || '')
    )
        return;
    const visitDate = new Date(enteredAt);
    if (!Number.isFinite(visitDate.getTime())) return;
    const visitTime = visitDate.toISOString();
    store.enterVisit({ accountId, location: currentLocation, enteredAt: visitTime, worldName });
    store.playerJoined({ userId: gameLog.userId, displayName: gameLog.displayName || '', observedAt: gameLog.dt });
}
