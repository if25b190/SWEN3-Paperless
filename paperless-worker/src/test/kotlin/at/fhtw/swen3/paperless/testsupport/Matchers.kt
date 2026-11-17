package at.fhtw.swen3.paperless.testsupport

import org.mockito.ArgumentMatchers

@Suppress("UNCHECKED_CAST")
fun <T> any(type: Class<T>): T = ArgumentMatchers.any(type) ?: (null as T)

@Suppress("UNCHECKED_CAST")
fun <T> any(): T = ArgumentMatchers.any<T>() ?: (null as T)
