import { beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
    calls: [],
    watchState: { isLoggedIn: true, isFriendsLoaded: true, isFavoritesLoaded: true },
    authStore: {
        updateStoredUser: vi.fn(async () => {}),
        loginForm: { lastUserLoggedIn: 'usr_me' },
        setAttemptingAutoLogin: vi.fn(),
        autoLoginAttempts: new Set()
    },
    userStore: { currentUser: { id: 'usr_me', displayName: 'Me' }, setUserDialogVisible: vi.fn() },
    notificationStore: { setNotificationInitStatus: vi.fn() }
}));

vi.mock('@/plugins/i18n', () => ({ i18n: { global: { t: () => 'goodbye' } } }));
vi.mock('noty', () => ({
    default: class {
        show() {}
    }
}));
vi.mock('@/services/websocket', () => ({ closeWebSocket: vi.fn(), initWebsocket: vi.fn() }));
vi.mock('@/shared/utils', () => ({ escapeTag: (value) => value }));
vi.mock('@/queries', () => ({ queryClient: { clear: vi.fn() } }));
vi.mock('@/stores/auth', () => ({ useAuthStore: () => mocks.authStore }));
vi.mock('@/stores/notification', () => ({ useNotificationStore: () => mocks.notificationStore }));
vi.mock('@/stores/updateLoop', () => ({ useUpdateLoopStore: () => ({}) }));
vi.mock('@/stores/user', () => ({ useUserStore: () => mocks.userStore }));
vi.mock('@/coordinators/userCoordinator', () => ({ applyCurrentUser: vi.fn() }));
vi.mock('@/services/watchState', () => ({ watchState: mocks.watchState }));
vi.mock('@/services/config', () => ({ default: { remove: vi.fn(async () => {}) } }));
vi.mock('@/services/webapi', () => ({ default: { clearCookies: vi.fn() } }));

import { runLogoutFlow } from '../authCoordinator';

describe('mobile companion logout boundary', () => {
    beforeEach(() => {
        mocks.calls.length = 0;
        mocks.watchState.isLoggedIn = true;
        mocks.watchState.isFriendsLoaded = true;
        mocks.authStore.updateStoredUser.mockImplementation(async () => mocks.calls.push('stored-user'));
        mocks.userStore.setUserDialogVisible.mockImplementation(() => mocks.calls.push('dialog'));
    });

    it('waits for native session invalidation before changing login state', async () => {
        let release;
        globalThis.AppApi = {
            MobileCompanionClearActiveAccount: vi.fn(() => {
                mocks.calls.push('native-clear');
                return new Promise((resolve) => {
                    release = resolve;
                });
            })
        };
        const pending = runLogoutFlow();
        expect(mocks.calls).toEqual(['native-clear']);
        expect(mocks.watchState.isLoggedIn).toBe(true);
        release();
        await pending;
        expect(mocks.watchState.isLoggedIn).toBe(false);
        expect(mocks.calls).toEqual(['native-clear', 'dialog', 'stored-user']);
        delete globalThis.AppApi;
    });
});
