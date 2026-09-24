<template>
    <SettingsGroup v-if="available" :title="t('view.settings.mobile_companion.header')">
        <template #description>
            <p class="m-0">{{ t('view.settings.mobile_companion.description') }}</p>
            <p class="m-0">{{ t('view.settings.mobile_companion.private_network') }}</p>
        </template>

        <SettingsItem :label="t('view.settings.mobile_companion.enable')">
            <Switch
                :model-value="state.enabled"
                :disabled="busy || !state.accountId || (!state.enabled && !state.availableAddresses.length)"
                :ariaLabel="t('view.settings.mobile_companion.enable')"
                @update:modelValue="setEnabled" />
        </SettingsItem>

        <div v-if="!state.accountId" class="text-sm text-muted-foreground">
            {{ t('view.settings.mobile_companion.login_required') }}
        </div>

        <template v-if="state.enabled">
            <div class="flex flex-wrap items-center gap-2 text-sm">
                <span>{{ t('view.settings.mobile_companion.address') }}:</span>
                <span class="font-mono">{{ state.address }}:{{ state.port }}</span>
            </div>
            <div class="flex flex-wrap items-center gap-2">
                <Button data-testid="mobile-companion-create-offer" size="sm" variant="outline" @click="createOffer">
                    {{ t('view.settings.mobile_companion.create_qr') }}
                </Button>
                <Button size="sm" variant="outline" @click="refresh">
                    {{ t('view.settings.mobile_companion.refresh') }}
                </Button>
            </div>
            <div v-if="qrData" class="flex flex-col items-start gap-2">
                <img
                    data-testid="pairing-qr"
                    :src="qrData"
                    :alt="t('view.settings.mobile_companion.qr_alt')"
                    class="rounded bg-white p-2 w-60 h-60" />
                <p class="text-xs text-muted-foreground m-0">{{ t('view.settings.mobile_companion.qr_expires') }}</p>
            </div>

            <div class="flex flex-col gap-2">
                <strong class="text-sm">{{ t('view.settings.mobile_companion.pending') }}</strong>
                <span v-if="pending.length === 0" class="text-sm text-muted-foreground">
                    {{ t('view.settings.mobile_companion.none') }}
                </span>
                <div
                    v-for="request in pending"
                    :key="request.requestId"
                    class="flex flex-wrap items-center gap-2 text-sm">
                    <span>{{ request.deviceName }}</span>
                    <Button :data-testid="`approve-${request.requestId}`" size="sm" @click="approve(request.requestId)">
                        {{ t('view.settings.mobile_companion.approve') }}
                    </Button>
                    <Button
                        :data-testid="`reject-${request.requestId}`"
                        size="sm"
                        variant="outline"
                        @click="reject(request.requestId)">
                        {{ t('view.settings.mobile_companion.reject') }}
                    </Button>
                </div>
            </div>
        </template>

        <template v-else-if="state.availableAddresses.length">
            <label class="flex items-center gap-2 text-sm">
                {{ t('view.settings.mobile_companion.address') }}
                <select v-model="selectedAddress" class="rounded border border-border bg-background p-1">
                    <option v-for="address in state.availableAddresses" :key="address" :value="address">
                        {{ address }}
                    </option>
                </select>
            </label>
        </template>

        <div v-if="state.accountId" class="flex flex-col gap-2">
            <strong class="text-sm">{{ t('view.settings.mobile_companion.devices') }}</strong>
            <span v-if="devices.length === 0" class="text-sm text-muted-foreground">
                {{ t('view.settings.mobile_companion.none') }}
            </span>
            <div v-for="device in devices" :key="device.deviceId" class="flex flex-wrap items-center gap-2 text-sm">
                <span>{{ device.deviceName }}</span>
                <span v-if="device.revoked" class="text-muted-foreground">{{
                    t('view.settings.mobile_companion.revoked')
                }}</span>
                <Button
                    v-else
                    :data-testid="`revoke-${device.deviceId}`"
                    size="sm"
                    variant="outline"
                    @click="revoke(device.deviceId)">
                    {{ t('view.settings.mobile_companion.revoke') }}
                </Button>
            </div>
        </div>

        <p v-if="errorText" data-testid="mobile-companion-error" role="alert" class="text-sm text-destructive m-0">
            {{ errorText }}
        </p>
    </SettingsGroup>
</template>

<script setup>
    import { onMounted, onUnmounted, reactive, ref } from 'vue';
    import QRCode from 'qrcode';
    import { useI18n } from 'vue-i18n';
    import { Button } from '@/components/ui/button';
    import { Switch } from '@/components/ui/switch';
    import SettingsGroup from './SettingsGroup.vue';
    import SettingsItem from './SettingsItem.vue';

    const { t } = useI18n();
    const available = typeof AppApi !== 'undefined' && typeof AppApi.MobileCompanionGetState === 'function';
    const state = reactive({ enabled: false, accountId: null, availableAddresses: [], address: null, port: null });
    const selectedAddress = ref('');
    const pending = ref([]);
    const devices = ref([]);
    const qrData = ref('');
    const errorText = ref('');
    const busy = ref(false);
    let qrExpiryTimer;

    function clearQr() {
        if (qrExpiryTimer) clearTimeout(qrExpiryTimer);
        qrExpiryTimer = undefined;
        qrData.value = '';
    }

    function parseResult(value) {
        return typeof value === 'string' ? JSON.parse(value) : value;
    }

    async function refresh() {
        if (!available) return;
        try {
            const next = parseResult(await AppApi.MobileCompanionGetState());
            Object.assign(state, next);
            if (!state.enabled || !state.accountId) clearQr();
            selectedAddress.value = state.availableAddresses.includes(selectedAddress.value)
                ? selectedAddress.value
                : (state.availableAddresses[0] ?? '');
            pending.value = state.enabled ? parseResult(await AppApi.MobileCompanionListPending()) : [];
            devices.value = state.accountId ? parseResult(await AppApi.MobileCompanionListDevices()) : [];
        } catch (error) {
            errorText.value = error?.message || t('view.settings.mobile_companion.error');
        }
    }

    async function setEnabled(next) {
        busy.value = true;
        errorText.value = '';
        if (!next) clearQr();
        try {
            if (next) await AppApi.MobileCompanionEnable(selectedAddress.value);
            else await AppApi.MobileCompanionDisable();
            await refresh();
        } catch (error) {
            errorText.value = error?.message || t('view.settings.mobile_companion.error');
        } finally {
            busy.value = false;
        }
    }

    async function createOffer() {
        errorText.value = '';
        clearQr();
        try {
            const offer = parseResult(await AppApi.MobileCompanionCreateOffer());
            qrData.value = await QRCode.toDataURL(JSON.stringify(offer), { width: 240, margin: 2 });
            qrExpiryTimer = setTimeout(clearQr, 2 * 60 * 1000);
        } catch (error) {
            errorText.value = error?.message || t('view.settings.mobile_companion.error');
        }
    }

    async function changeDevice(method, id) {
        errorText.value = '';
        try {
            await AppApi[method](id);
            await refresh();
        } catch (error) {
            errorText.value = error?.message || t('view.settings.mobile_companion.error');
        }
    }

    const approve = (id) => changeDevice('MobileCompanionApprove', id);
    const reject = (id) => changeDevice('MobileCompanionReject', id);
    const revoke = (id) => changeDevice('MobileCompanionRevoke', id);

    onMounted(refresh);
    onUnmounted(clearQr);
</script>
