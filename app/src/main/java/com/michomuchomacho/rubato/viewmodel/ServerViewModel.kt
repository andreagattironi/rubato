package com.michomuchomacho.rubato.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.michomuchomacho.rubato.model.Server
import com.michomuchomacho.rubato.repository.ServerRepository

class ServerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ServerRepository()
    val allServers: LiveData<List<Server>> = repository.liveServer

    fun insertServer(server: Server) {
        repository.insert(server)
    }

    fun updateServer(server: Server) {
        repository.update(server)
    }

    fun deleteServer(server: Server) {
        repository.delete(server)
    }
}