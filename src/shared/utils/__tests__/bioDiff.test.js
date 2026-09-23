import { describe, expect, it } from 'vitest';

import { formatBioDifference } from '../bioDiff.js';

describe('formatBioDifference', () => {
    it('marks additions and deletions while retaining line breaks', () => {
        const html = formatBioDifference('hello\nold', 'hello\nnew');
        expect(html).toContain('x-text-removed');
        expect(html).toContain('x-text-added');
        expect(html).toContain('<br>');
    });

    it('escapes user text before placing it in HTML', () => {
        const html = formatBioDifference('', '<img src=x onerror="alert(1)">&');
        expect(html).toContain('&lt;img');
        expect(html).toContain('&quot;alert(1)&quot;');
        expect(html).toContain('&amp;');
        expect(html).not.toContain('<img');
    });

    it('handles clearing and adding an empty bio', () => {
        expect(formatBioDifference('old', '')).toContain('x-text-removed');
        expect(formatBioDifference('', 'new')).toContain('x-text-added');
    });
});
