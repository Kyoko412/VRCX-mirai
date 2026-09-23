import { describe, expect, it } from 'vitest';

import { createBioChangeEntry } from '../bioHistoryEvents.js';

describe('friend bio change entries', () => {
    const friend = { id: 'usr_friend', displayName: 'Friend' };
    const time = () => '2026-09-23T10:00:00.000Z';

    it('records a normal edit and changes to and from an empty bio', () => {
        expect(createBioChangeEntry(friend, ['B', 'A'], time)).toMatchObject({ bio: 'B', previousBio: 'A' });
        expect(createBioChangeEntry(friend, ['', 'A'], time)).toMatchObject({ bio: '', previousBio: 'A' });
        expect(createBioChangeEntry(friend, ['A', ''], time)).toMatchObject({ bio: 'A', previousBio: '' });
    });

    it('ignores absent values and unchanged text', () => {
        expect(createBioChangeEntry(friend, ['A', 'A'], time)).toBeNull();
        expect(createBioChangeEntry(friend, [null, 'A'], time)).toBeNull();
        expect(createBioChangeEntry(friend, undefined, time)).toBeNull();
    });
});
