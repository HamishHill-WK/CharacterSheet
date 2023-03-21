package com.example.charactersheet

import android.app.Activity
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: Activity
    private lateinit var navController: NavController


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

}

class DiceButton {      //class for ability score button, holds bonus value for current score.
    var Bonus = 0
    var RawRollResult = 0

     fun roll(sides: Int): Int{
         println("roll")
         RawRollResult = (1..sides).random()
         return RawRollResult + Bonus
    }

     fun setASBonus (score: String){
        when(score){
            "-5" ->{ println("-5")
                    Bonus = -5}
            "-4" -> Bonus = -4
            "-3" -> Bonus = -3
            "-2" -> Bonus = -2
            "-1" -> Bonus = -1
            "0" -> Bonus = 0
            "+1" -> Bonus = 1
            "+2" -> Bonus = 2
            "+3" -> Bonus = 3
            "+4" -> Bonus = 4
            "+5" -> Bonus = 5
            "+6" -> Bonus = 6
            "+7" -> Bonus = 7
            "+8" -> Bonus = 8
            "+9" -> Bonus = 9
            "+10" -> Bonus = 10
        }
    }
}