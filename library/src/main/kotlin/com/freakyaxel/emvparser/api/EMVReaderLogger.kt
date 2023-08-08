package com.freakyaxel.emvparser.api

interface EMVReaderLogger {
    fun emvLog(key: String, value: String)

}