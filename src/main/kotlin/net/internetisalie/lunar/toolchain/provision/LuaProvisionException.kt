package net.internetisalie.lunar.toolchain.provision

/**
 * Raised by native-provisioning code for any recoverable, user-facing failure:
 * a corrupt bundled feed, an unresolvable version spec, an unsupported host, a
 * download/verification/build failure, etc. Actions surface it as an ERROR balloon.
 */
class LuaProvisionException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
