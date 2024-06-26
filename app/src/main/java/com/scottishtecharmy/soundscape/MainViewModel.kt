package com.scottishtecharmy.soundscape

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scottishtecharmy.soundscape.network.ITiles
import com.scottishtecharmy.soundscape.network.Tiles
import kotlinx.coroutines.launch

class MainViewModel(var tileService: ITiles = Tiles()): ViewModel() {
    var tile: MutableLiveData<String?> = MutableLiveData<String?>()

    fun getTile(xTile: Int, yTile: Int) {
        viewModelScope.launch {
            val innerTile = tileService.getTile(xTile, yTile).toString()
            tile.postValue(innerTile)
        }
    }

}