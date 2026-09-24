package com.kyoko412.vrcxcompanion.ui

enum class ConnectionState(val label: String) {
    Loading("正在连接"),
    Ready("已连接"),
    Offline("电脑离线或服务未开启，请检查电脑后重试"),
    WifiMismatch("手机未连接 Wi-Fi，请与电脑连接到同一网络"),
    PermissionDenied("局域网访问权限未开启，请允许后重试"),
    Revoked("此手机的访问已撤销，请重新配对"),
    AccountChanged("电脑已切换账号，请在电脑确认后刷新"),
    IncompatibleVersion("电脑版本与手机端不兼容，请更新应用"),
    Timeout("电脑响应超时，请重试"),
    IdentityChanged("电脑身份发生变化，请核对后重新配对"),
    Unavailable("电脑数据暂不可用，请稍后重试")
}
