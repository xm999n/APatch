Modify apd/src/cli.rs:100-102: Log only errors.

launchericon https://github.com/bmax121/APatch/commit/118bca9fc73d3d1403dbd9faec2e498f1fd9b547#diff-f10ece1019a63c3d819fbe608ab42b8efb524d9b86b5e1a361f1f37c7ea2deca

amoled

Available only in Simplified Chinese and English

<<<<<<< HEAD
arm64 only
=======
The patching of Android kernel and Android system.

- A new kernel-based root solution for Android devices.
- APM: Support for modules similar to Magisk.
- KPM: Support for modules that allow you to inject any code into the kernel (Provides kernel function `inline-hook` and `syscall-table-hook`).
- APatch relies on [KernelPatch](https://github.com/bmax121/KernelPatch/).
- The APatch UI and the APModule source code have been derived and modified from [KernelSU](https://github.com/tiann/KernelSU).

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/me.bmax.apatch/)

Or download the latest APK from the [Releases Section](https://github.com/bmax121/APatch/releases/latest).

## Supported Versions

- Only supports the ARM64 architecture.
- Only supports Android kernel versions 3.18 - 6.12

Support for Samsung devices with security protection: Planned

## Requirement

Kernel configs:

- `CONFIG_KALLSYMS=y` and `CONFIG_KALLSYMS_ALL=y`

- `CONFIG_KALLSYMS=y` and `CONFIG_KALLSYMS_ALL=n`: Initial support

## Security Alert

The **SuperKey** has higher privileges than root access.  
Weak or compromised keys can lead to unauthorized control of your device.  
It is critical to use robust keys and safeguard them from exposure to maintain the security of your device.

## Translation

To help translate APatch or improve existing translations, please use [Weblate](https://hosted.weblate.org/engage/apatch/). PR of APatch translation is no longer accepted, because it will conflict with Weblate.

<div align="center">

[![Translation Status](https://hosted.weblate.org/widget/APatch/open-graph.png)](https://hosted.weblate.org/engage/APatch/)

</div>

## Get Help

### Usage

For usage, please refer to [our official documentation](https://apatch.dev).  
It's worth noting that the documentation is currently not quite complete, and the content may change at any time.  
Furthermore, we need more volunteers to [contribute to the documentation](https://github.com/AndroidPatch/APatchDocs) in other languages.

### Updates

- Telegram Channel: [@APatchUpdates](https://t.me/APatchChannel)

### Discussions

- Telegram Group: [@APatchDiscussions(EN/CN)](https://t.me/Apatch_discuss)
- Telegram Group: [中文](https://t.me/APatch_CN_Group)

### More Information

- [Documents](docs/)

## Credits

- [KernelPatch](https://github.com/bmax121/KernelPatch/): The core.
- [Magisk](https://github.com/topjohnwu/Magisk): magiskpolicy.
- [KernelSU](https://github.com/tiann/KernelSU): App UI, and Magisk module like support.

## License

APatch is licensed under the GNU General Public License v3 [GPL-3](http://www.gnu.org/copyleft/gpl.html).
>>>>>>> 38514ba (drop magiskboot)
