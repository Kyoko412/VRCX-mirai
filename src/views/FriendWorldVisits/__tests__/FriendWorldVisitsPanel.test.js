import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
    getFriendWorldVisitEvents: vi.fn(),
    showWorldDialog: vi.fn()
}));

vi.mock('../../../services/database', () => ({
    database: { getFriendWorldVisitEvents: mocks.getFriendWorldVisitEvents }
}));
vi.mock('../../../coordinators/worldCoordinator', () => ({ showWorldDialog: mocks.showWorldDialog }));
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key) => key }) }));

import FriendWorldVisitsPanel from '../FriendWorldVisitsPanel.vue';

describe('FriendWorldVisitsPanel', () => {
    beforeEach(() => {
        mocks.getFriendWorldVisitEvents.mockReset();
        mocks.showWorldDialog.mockReset();
    });

    it('loads the selected friend and replaces their history when the selection changes', async () => {
        mocks.getFriendWorldVisitEvents.mockImplementation(async (userId) => [
            {
                created_at: '2026-09-01T10:00:00.000Z',
                type: 'Online',
                location: userId === 'usr_a' ? 'wrld_alpha:1' : 'wrld_beta:1',
                worldName: userId === 'usr_a' ? 'Alpha' : 'Beta'
            }
        ]);

        const wrapper = mount(FriendWorldVisitsPanel, { props: { userId: 'usr_a' } });
        await flushPromises();
        expect(wrapper.text()).toContain('Alpha');
        expect(wrapper.text()).toContain('view.friend_world_visits.exit_unknown');

        await wrapper.setProps({ userId: 'usr_b' });
        await flushPromises();
        expect(wrapper.text()).toContain('Beta');
        expect(wrapper.text()).not.toContain('Alpha');
        expect(mocks.getFriendWorldVisitEvents).toHaveBeenCalledWith('usr_b', expect.any(String));
    });

    it('lets the user request all recorded history', async () => {
        mocks.getFriendWorldVisitEvents.mockResolvedValue([]);
        const wrapper = mount(FriendWorldVisitsPanel, { props: { userId: 'usr_a' } });
        await flushPromises();

        await wrapper.get('select').setValue('all');
        await flushPromises();

        expect(mocks.getFriendWorldVisitEvents).toHaveBeenLastCalledWith('usr_a', '');
    });
});
