package pt.sergiotaveira.mercedes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import pt.sergiotaveira.mercedes.data.CarRepository

class CarViewModel(repo: CarRepository) : ViewModel() {
    val controller = CarController(repo, viewModelScope)
}
