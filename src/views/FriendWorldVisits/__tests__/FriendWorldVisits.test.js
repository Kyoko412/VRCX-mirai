import { mount } from '@vue/test-utils';
import { reactive } from 'vue';
import { describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({ push: vi.fn() }));
const route = reactive({ query: { userId: 'usr_a' } });
const friends = reactive(
    new Map([
        ['usr_a', { id: 'usr_a', name: 'Alice' }],
        ['usr_b', { id: 'usr_b', name: 'Bob' }]
    ])
);

vi.mock('vue-router', () => ({
    useRoute: () => route,
    useRouter: () => ({ push: mocks.push })
}));
vi.mock('../../../stores/friend', () => ({ useFriendStore: () => ({ friends }) }));
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key) => key }) }));
vi.mock('../FriendWorldVisitsPanel.vue', () => ({
    default: { props: ['userId'], template: '<div data-testid="visits">{{ userId }}</div>' }
}));

import FriendWorldVisits from '../FriendWorldVisits.vue';

describe('FriendWorldVisits', () => {
    it('opens the selected friend’s visit history when their name is clicked', async () => {
        const wrapper = mount(FriendWorldVisits, {
            global: {
                stubs: {
                    InputGroupSearch: { template: '<input />' }
                }
            }
        });

        expect(wrapper.find('[data-testid="visits"]').text()).toBe('usr_a');
        await wrapper
            .findAll('button')
            .find((button) => button.text().includes('Bob'))
            .trigger('click');
        expect(mocks.push).toHaveBeenCalledWith({
            name: 'friend-world-visits',
            query: { userId: 'usr_b' }
        });
    });
});
