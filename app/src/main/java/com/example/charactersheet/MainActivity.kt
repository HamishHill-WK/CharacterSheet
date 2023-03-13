package com.example.charactersheet

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: Activity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val button: List<ImageButton> = listOf(findViewById(R.id.StrengthButton),
                                                findViewById(R.id.DexButton),
                                                findViewById(R.id.ConButton),
                                                findViewById(R.id.IntButton),
                                                findViewById(R.id.WisButton),
                                                findViewById(R.id.ChaButton))

        val abilityScores: List<EditText> = listOf(findViewById(R.id.StrengthScore),
                                                findViewById(R.id.DexScore),
                                                findViewById(R.id.ConScore),
                                                findViewById(R.id.IntScore),
                                                findViewById(R.id.WisScore),
                                                findViewById(R.id.ChaScore))

        val abilityScoreBonus: List<TextView> = listOf(findViewById(R.id.StrengthBonus),
                                                findViewById(R.id.DexBonus),
                                                findViewById(R.id.ConBonus),
                                                findViewById(R.id.IntBonus),
                                                findViewById(R.id.WisBonus),
                                                findViewById(R.id.ChaBonus))

        var AbilityDice: MutableList<DiceButton> = mutableListOf(DiceButton())

        var s: Int
        for ((d,x) in button.withIndex()) { //create dice class object for all ability scores
            var dice = DiceButton()
            AbilityDice.add(dice)
            x.setOnClickListener {
                s = AbilityDice[d].roll(20)
                val resultText: TextView = findViewById(R.id.RollResult)
                resultText.text = "$s = ${AbilityDice[d].RawRollResult} + Bonus: ${AbilityDice[d].Bonus} "
                println(s)
            }
        }

        for ((c, y) in abilityScores.withIndex()){  //when the player changes the text of an ability score
                                                    //update bonus scores
            y.addTextChangedListener (object : TextWatcher {

                    override fun afterTextChanged(s: Editable?) {
                        abilityScoreBonus[c].text = setASBonus(y.text.toString())
                        AbilityDice[c].setASBonus(abilityScoreBonus[c].text.toString())
                    }

                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    }

                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        println("text touched")
                    }
                })

        }
    }

    private fun setASBonus (score: String): String{
        var newBonus = ""
        when(score){
            "1" -> newBonus = "-5"
            "2","3" -> newBonus = "-4"
            "4","5" -> newBonus = "-3"
            "6","7" -> newBonus = "-2"
            "8","9" -> newBonus = "-1"
            "10","11" -> newBonus = "0"
            "12","13" -> newBonus = "+1"
            "14","15" -> newBonus = "+2"
            "16","17" -> newBonus = "+3"
            "18","19" -> newBonus = "+4"
            "20","21" -> newBonus = "+5"
            "22","23" -> newBonus = "+6"
            "24","25" -> newBonus = "+7"
            "26","27" -> newBonus = "+8"
            "28","29" -> newBonus = "+9"
            "30" -> newBonus = "+10"
        }
        return newBonus
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