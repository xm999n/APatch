mod apd;
mod assets;
mod cli;
mod defs;
mod event;
mod magic_mount;
mod lua;
mod module;
mod mount;
mod module_config;
mod package;
#[cfg(any(target_os = "linux", target_os = "android"))]
mod pty;
mod resetprop;
mod restorecon;
mod sepolicy;
mod supercall;
mod utils;
fn main() -> anyhow::Result<()> {
    cli::run()
}
