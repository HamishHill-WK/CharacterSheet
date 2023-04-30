package com.example.charactersheet

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import com.example.charactersheet.databinding.FragmentPopUpBinding

//intermediary fragment which allows the user to confirm the detected result before it is sent to the server - hh

class PopUpFragment : Fragment() {
    private var _binding: FragmentPopUpBinding? = null

    private val binding get() = _binding!!

    private lateinit var recyclerView: FrameLayout

    private lateinit var letterId: String

    private lateinit var resultText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            letterId = it.getString(FrontFragment.RESULT).toString()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentPopUpBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = binding.root

        resultText = binding.resultsText

        if(letterId != null) {
            resultText.text = letterId
        }

        binding.YesButton.setOnClickListener{
            val action = PopUpFragmentDirections.actionPopUpFragmentToFrontFragment(letterId)
            view.findNavController().navigate(action)
        }

        binding.resultsText.addTextChangedListener (object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                letterId = s.toString()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }//these 2 overrides need to be declared or this will not compile
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }
        })

        binding.NoButton.setOnClickListener{
            val action = PopUpFragmentDirections.actionPopUpFragmentToCameraFragment()
            view.findNavController().navigate(action)
        }
    }

    companion object {
        val TAG = "PopUpFragment.kt"
    }
}