import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';

const qr = vi.hoisted(() => ({ toDataURL: vi.fn().mockResolvedValue('data:image/png;base64,abc') }));
vi.mock('qrcode', () => ({ default: qr }));
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (key) => key }) }));
vi.mock('@/components/ui/switch', () => ({
    Switch: {
        props: ['modelValue', 'disabled'],
        emits: ['update:modelValue'],
        template:
            '<button data-testid="mobile-companion-enable" :disabled="disabled" @click="$emit(\'update:modelValue\', !modelValue)"></button>'
    }
}));
vi.mock('@/components/ui/button', () => ({
    Button: { template: '<button @click="$emit(\'click\')"><slot /></button>' }
}));
vi.mock('../SettingsGroup.vue', () => ({
    default: { template: '<section><slot /><slot name="description" /></section>' }
}));
vi.mock('../SettingsItem.vue', () => ({
    default: { template: '<div><slot /></div>' }
}));

import MobileCompanionSettings from '../MobileCompanionSettings.vue';

describe('MobileCompanionSettings', () => {
    let api;
    let enabled;
    let pending;
    let devices;

    beforeEach(() => {
        enabled = false;
        pending = [];
        devices = [];
        qr.toDataURL.mockClear();
        api = {
            MobileCompanionGetState: vi.fn(() =>
                Promise.resolve(
                    JSON.stringify({
                        enabled,
                        accountId: 'usr_me',
                        availableAddresses: ['192.168.1.10'],
                        address: enabled ? '192.168.1.10' : null,
                        port: enabled ? 34682 : null
                    })
                )
            ),
            MobileCompanionEnable: vi.fn(() => {
                enabled = true;
                return Promise.resolve('{}');
            }),
            MobileCompanionDisable: vi.fn(() => {
                enabled = false;
                return Promise.resolve('{}');
            }),
            MobileCompanionCreateOffer: vi.fn(() =>
                Promise.resolve(
                    JSON.stringify({
                        v: 1,
                        address: '192.168.1.10',
                        port: 34682,
                        host: 'vrcx-companion.invalid',
                        spkiSha256: 'sha256/pin',
                        secret: 'secret'
                    })
                )
            ),
            MobileCompanionListPending: vi.fn(() => Promise.resolve(JSON.stringify(pending))),
            MobileCompanionApprove: vi.fn(() => Promise.resolve()),
            MobileCompanionReject: vi.fn(() => Promise.resolve()),
            MobileCompanionListDevices: vi.fn(() => Promise.resolve(JSON.stringify(devices))),
            MobileCompanionRevoke: vi.fn(() => Promise.resolve())
        };
        globalThis.AppApi = api;
    });

    afterEach(() => {
        delete globalThis.AppApi;
    });

    it('starts off and keeps the QR hidden until explicitly created', async () => {
        const wrapper = mount(MobileCompanionSettings);
        await flushPromises();
        expect(wrapper.find('[data-testid="pairing-qr"]').exists()).toBe(false);
        await wrapper.get('[data-testid="mobile-companion-enable"]').trigger('click');
        await flushPromises();
        expect(api.MobileCompanionEnable).toHaveBeenCalledWith('192.168.1.10');
        expect(wrapper.find('[data-testid="pairing-qr"]').exists()).toBe(false);
        await wrapper.get('[data-testid="mobile-companion-create-offer"]').trigger('click');
        await flushPromises();
        expect(wrapper.get('[data-testid="pairing-qr"]').attributes('src')).toContain('data:image/png');
    });

    it('approves, rejects and revokes individual devices', async () => {
        enabled = true;
        pending = [
            { requestId: 'req1', deviceName: 'Phone A' },
            { requestId: 'req2', deviceName: 'Phone B' }
        ];
        devices = [{ deviceId: 'dev1', deviceName: 'Old phone', revoked: false }];
        const wrapper = mount(MobileCompanionSettings);
        await flushPromises();
        await wrapper.get('[data-testid="approve-req1"]').trigger('click');
        await wrapper.get('[data-testid="reject-req2"]').trigger('click');
        await wrapper.get('[data-testid="revoke-dev1"]').trigger('click');
        expect(api.MobileCompanionApprove).toHaveBeenCalledWith('req1');
        expect(api.MobileCompanionReject).toHaveBeenCalledWith('req2');
        expect(api.MobileCompanionRevoke).toHaveBeenCalledWith('dev1');
    });

    it('allows revoking a previously paired phone while access is off', async () => {
        enabled = false;
        devices = [{ deviceId: 'dev1', deviceName: 'Old phone', revoked: false }];
        const wrapper = mount(MobileCompanionSettings);
        await flushPromises();
        await wrapper.get('[data-testid="revoke-dev1"]').trigger('click');
        expect(api.MobileCompanionRevoke).toHaveBeenCalledWith('dev1');
    });

    it('shows errors and hides itself when CEF bridge is unavailable', async () => {
        api.MobileCompanionEnable.mockRejectedValueOnce(new Error('Cannot bind'));
        const wrapper = mount(MobileCompanionSettings);
        await flushPromises();
        await wrapper.get('[data-testid="mobile-companion-enable"]').trigger('click');
        await flushPromises();
        expect(wrapper.get('[data-testid="mobile-companion-error"]').text()).toContain('Cannot bind');
        wrapper.unmount();
        delete globalThis.AppApi;
        expect(mount(MobileCompanionSettings).html()).toBe('<!--v-if-->');
    });
});
