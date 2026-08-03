# OpenLink 2

A multiplayer mod project that is not finished yet.

## TODO

- cn.scarefree.openlink2
  - mixin(used when needed)
  - platform(template)
  - api
    - account
      - [x] Account(interface)
      - [x] AccountManager(interface)
      - [x] AccountPlatform(interface)
      - [x] AccountStore(interface)
      - [x] LoginRequestInfo(class)
      - [x] LoginFlowType(enum)
      - [x] AuthException(exception)
      - [x] PlatformNotSupportedException(exception)
      - [x] TokenExpiredException(exception)
    - multiplayer
      - [ ] MultiplayerSettings(interface) - just an interface to get/set all the settings, could be got in MultiplayerService
      - [ ] MultiplayerService<T extends AccountPlatform>(interface) - could be used with a AccountPlatform or Void.
      - [ ] MultiplayerManager(interface)
  - impl
    - account
      - [x] AccountImpl
      - [x] AccountManagerImpl
      - [x] JsonAccountStore - temp usage, will be replaced(maybe)
      - [ ] NatayarkIdAccountPlatform
      - [ ] ELinkAccountPlatform - unknown
    - multiplayer
  - gui
  - logic
