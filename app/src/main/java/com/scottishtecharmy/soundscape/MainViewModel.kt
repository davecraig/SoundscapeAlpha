package com.scottishtecharmy.soundscape

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scottishtecharmy.soundscape.network.ITiles
import com.scottishtecharmy.soundscape.network.Tiles
import kotlinx.coroutines.launch

class MainViewModel(var tileService: ITiles = Tiles()): ViewModel() {
    var tile: MutableLiveData<String?> = MutableLiveData<String?>()

    fun getTile(xtile: Int, ytile: Int) {
        viewModelScope.launch {
            val innerTile = tileService.getTile(xtile, ytile).toString()
            tile.postValue(innerTile)
        }
    }

}