/** Capture the account at event time; replayed and logged-out events have no provable owner. */
export function ownerForLiveGameLocation({ trackEncounter, isLoggedIn, accountId }) {
    if (!trackEncounter || !isLoggedIn || typeof accountId !== 'string') return null;
    return /^usr_[A-Za-z0-9_-]{1,64}$/.test(accountId) ? accountId : null;
}
