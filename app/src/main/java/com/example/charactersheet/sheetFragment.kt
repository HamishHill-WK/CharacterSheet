package com.example.charactersheet

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import com.example.charactersheet.databinding.FragmentSheetBinding

class SheetFragment : Fragment() {

    private var _binding: FragmentSheetBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private lateinit var recyclerView: FrameLayout

    // Keeps track of which LayoutManager is in use for the [RecyclerView]
    private var isLinearLayoutManager = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        _binding = FragmentSheetBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recyclerView = binding.root

        val button: List<ImageButton> = listOf(binding.StrengthButton,
            binding.DexButton,
            binding.ConButton,
            binding.IntButton,
            binding.WisButton,
            binding.ChaButton)

        val abilityScores: List<EditText> = listOf(binding.StrengthScore,
            binding.DexScore,
            binding.ConScore,
            binding.IntScore,
            binding.WisScore,
            binding.ChaScore)

        val abilityScoreBonus: List<TextView> = listOf(binding.StrengthBonus,
            binding.DexBonus,
            binding.ConBonus,
            binding.IntBonus,
            binding.WisBonus,
            binding.ChaBonus)

        var AbilityDice: MutableList<DiceButton> = mutableListOf(DiceButton())

        var s: Int
        for ((d,x) in button.withIndex()) { //create dice class object for all ability scores
            var dice = DiceButton()
            AbilityDice.add(dice)
            x.setOnClickListener {
                s = AbilityDice[d].roll(20)
                val resultText: TextView = binding.RollResult
                resultText.text = "$s = ${AbilityDice[d].RawRollResult} + Bonus: ${AbilityDice[d].Bonus} "
                println(s)

                val action = SheetFragmentDirections.actionSheetFragmentToCameraFragment()
                //LetterListFragmentDirections.actionLetterListFragmentToWordListFragment("d8")
                // Navigate using that action
                view.findNavController().navigate(action)
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

    companion object {

    }
}