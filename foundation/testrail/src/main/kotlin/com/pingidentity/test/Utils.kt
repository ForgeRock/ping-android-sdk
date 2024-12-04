/*
 * Copyright (c) 2024 Ping Identity. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license. See the LICENSE file for details.
 */

package com.pingidentity.test

import java.io.File

fun readFile(fileName: String) : String {
    val classLoader = Thread.currentThread().contextClassLoader
    val fileUrl = classLoader?.getResource(fileName)
        ?: throw IllegalArgumentException("File not found: $fileName")
    return File(fileUrl.toURI()).readText(Charsets.UTF_8)
}