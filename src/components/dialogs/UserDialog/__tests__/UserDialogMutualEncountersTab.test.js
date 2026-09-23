import { mount, flushPromises } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
    getMutualEncounterHistory: vi.fn()
}));

vi.mock('../../../../services/database', () => ({
    database: { getMutualEncounterHistory: mocks.getMutualEncounterHistory },
    dbVars: { userId: 'usr_me' }
}));
vi.mock('vue-i18n', () => ({
    useI18n: () => ({ t: (key, args) => (args?.count === undefined ? key : `friends:${args.count}`) })
}));

import UserDialogMutualEncountersTab from '../UserDialogMutualEncountersTab.vue';

describe('mutual encounter history tab', () => {
    it('shows the saved world, instance, date, and mutual count for one player', async () => {
        mocks.getMutualEncounterHistory.mockResolvedValueOnce([
            {
                observedAt: '2026-09-23T10:00:10.000Z',
                location: 'wrld_alpha:1',
                worldName: 'World Alpha',
                mutualFriendCount: 2
            }
        ]);

        const wrapper = mount(UserDialogMutualEncountersTab, { props: { userId: 'usr_other' } });
        await flushPromises();

        expect(mocks.getMutualEncounterHistory).toHaveBeenCalledWith('usr_other');
        expect(wrapper.text()).toContain('World Alpha');
        expect(wrapper.text()).toContain('wrld_alpha:1');
        expect(wrapper.text()).toContain('2026');
        expect(wrapper.text()).toContain('friends:2');
    });
});
