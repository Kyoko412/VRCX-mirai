export function createBioChangeEntry(user, change, nowIso = () => new Date().toISOString()) {
    if (!user?.id || !Array.isArray(change) || change.length < 2) return null;
    const [bio, previousBio] = change;
    if (typeof bio !== 'string' || typeof previousBio !== 'string' || bio === previousBio) return null;
    return {
        created_at: nowIso(),
        type: 'Bio',
        userId: user.id,
        displayName: user.displayName || '',
        bio,
        previousBio
    };
}
