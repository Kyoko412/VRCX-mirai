import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
    getFriendBioHistory: vi.fn(),
    getFriendBioHistoryCount: vi.fn()
}));
vi.mock('../../../../services/database', () => ({ database: mocks }));
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key, values) => `${key}${values?.count ?? ''}` }) }));
vi.mock('../../../../shared/utils', () => ({ formatDateFilter: (value) => value }));
vi.mock('@/components/ui/dialog', () => ({
    Dialog: { template: '<div><slot /></div>' },
    DialogContent: { template: '<div><slot /></div>' },
    DialogHeader: { template: '<div><slot /></div>' },
    DialogTitle: { template: '<div><slot /></div>' },
    DialogDescription: { template: '<div><slot /></div>' }
}));
vi.mock('@/components/ui/button', () => ({
    Button: { template: '<button @click="$emit(\'click\')"><slot /></button>' }
}));

import UserDialogBioHistoryDialog from '../UserDialogBioHistoryDialog.vue';

const stubs = {
    Dialog: { template: '<div><slot /></div>' },
    DialogContent: { template: '<div><slot /></div>' },
    DialogHeader: { template: '<div><slot /></div>' },
    DialogTitle: { template: '<div><slot /></div>' },
    DialogDescription: { template: '<div><slot /></div>' },
    Button: { template: '<button @click="$emit(\'click\')"><slot /></button>' }
};

describe('UserDialogBioHistoryDialog', () => {
    beforeEach(() => {
        mocks.getFriendBioHistory.mockReset();
        mocks.getFriendBioHistoryCount.mockReset();
    });

    it('loads the current account history for the selected user and escapes profile text', async () => {
        mocks.getFriendBioHistoryCount.mockResolvedValue(1);
        mocks.getFriendBioHistory.mockResolvedValue({
            rows: [{ id: 1, observedAt: '2026-09-23T10:00:00.000Z', previousBio: '', bio: '<img src=x>' }],
            nextCursor: null
        });
        const wrapper = mount(UserDialogBioHistoryDialog, {
            props: { open: true, userId: 'usr_one', displayName: 'Friend', currentBio: '<img src=x>' },
            global: { stubs }
        });
        await flushPromises();
        expect(mocks.getFriendBioHistory).toHaveBeenCalledWith('usr_one');
        expect(wrapper.text()).toContain('Friend');
        expect(wrapper.text()).toContain('<img src=x>');
        expect(wrapper.find('img[src="x"]').exists()).toBe(false);
    });

    it('offers retry after a local database error', async () => {
        mocks.getFriendBioHistoryCount.mockRejectedValueOnce(new Error('database error')).mockResolvedValueOnce(0);
        mocks.getFriendBioHistory.mockResolvedValue({ rows: [], nextCursor: null });
        const wrapper = mount(UserDialogBioHistoryDialog, {
            props: { open: true, userId: 'usr_one', displayName: 'Friend', currentBio: '' },
            global: { stubs }
        });
        await flushPromises();
        expect(wrapper.text()).toContain('dialog.user.info.bio_history_error');
        await wrapper.find('[data-testid="bio-history-retry"]').trigger('click');
        await flushPromises();
        expect(mocks.getFriendBioHistoryCount).toHaveBeenCalledTimes(2);
    });

    it('labels an unrecorded current version and an empty local history accurately', async () => {
        mocks.getFriendBioHistoryCount.mockResolvedValue(0);
        mocks.getFriendBioHistory.mockResolvedValue({ rows: [], nextCursor: null });
        const wrapper = mount(UserDialogBioHistoryDialog, {
            props: { open: true, userId: 'usr_one', displayName: 'Friend', currentBio: 'current' },
            global: { stubs }
        });
        await flushPromises();
        expect(wrapper.text()).toContain('bio_history_current_unknown');
        expect(wrapper.text()).toContain('bio_history_none');
        expect(wrapper.text()).toContain('bio_history_count0');
    });

    it('does not treat an unavailable current profile as an empty bio', async () => {
        mocks.getFriendBioHistoryCount.mockResolvedValue(1);
        mocks.getFriendBioHistory.mockResolvedValue({
            rows: [{ id: 1, observedAt: '2026-09-23T10:00:00.000Z', previousBio: 'A', bio: '' }],
            nextCursor: null
        });
        const wrapper = mount(UserDialogBioHistoryDialog, {
            props: { open: true, userId: 'usr_one', displayName: 'Friend' },
            global: { stubs }
        });
        await flushPromises();
        expect(wrapper.text()).toContain('bio_history_current_unavailable');
        expect(wrapper.text()).not.toContain('bio_history_last_observed');
    });

    it('loads later pages using the returned cursor', async () => {
        const cursor = { createdAt: '2026-09-23T10:00:00.000Z', id: 2 };
        mocks.getFriendBioHistoryCount.mockResolvedValue(2);
        mocks.getFriendBioHistory
            .mockResolvedValueOnce({
                rows: [{ id: 2, observedAt: cursor.createdAt, previousBio: 'A', bio: 'B' }],
                nextCursor: cursor
            })
            .mockResolvedValueOnce({
                rows: [{ id: 1, observedAt: '2026-09-22T10:00:00.000Z', previousBio: '', bio: 'A' }],
                nextCursor: null
            });
        const wrapper = mount(UserDialogBioHistoryDialog, {
            props: { open: true, userId: 'usr_one', displayName: 'Friend', currentBio: 'B' },
            global: { stubs }
        });
        await flushPromises();
        await wrapper
            .findAll('button')
            .find((button) => button.text().includes('bio_history_load_more'))
            .trigger('click');
        await flushPromises();
        expect(mocks.getFriendBioHistory).toHaveBeenNthCalledWith(2, 'usr_one', { cursor });
        expect(wrapper.text()).toContain('bio_history_change1');
    });

    it('ignores a previous user’s query after switching profiles', async () => {
        let resolveOld;
        mocks.getFriendBioHistoryCount.mockResolvedValue(1);
        mocks.getFriendBioHistory
            .mockImplementationOnce(
                () =>
                    new Promise((resolve) => {
                        resolveOld = resolve;
                    })
            )
            .mockResolvedValueOnce({
                rows: [{ id: 3, observedAt: '2026-09-23', previousBio: '', bio: 'new user' }],
                nextCursor: null
            });
        const wrapper = mount(UserDialogBioHistoryDialog, {
            props: { open: true, userId: 'usr_old', displayName: 'Old', currentBio: '' },
            global: { stubs }
        });
        await wrapper.setProps({ userId: 'usr_new', displayName: 'New' });
        await flushPromises();
        resolveOld({ rows: [{ id: 1, observedAt: '2026-09-22', previousBio: '', bio: 'old user' }], nextCursor: null });
        await flushPromises();
        expect(wrapper.text()).toContain('new user');
        expect(wrapper.text()).not.toContain('old user');
    });
});
