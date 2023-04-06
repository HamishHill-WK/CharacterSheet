package com.example.charactersheet

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import com.example.charactersheet.databinding.FragmentPopUpBinding

class PopUpFragment : Fragment() {
    private var _binding: FragmentPopUpBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private lateinit var recyclerView: FrameLayout

    private lateinit var letterId: String

    private lateinit var resultText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            letterId = it.getString(SheetFragment.RESULT).toString()
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
        //return inflater.inflate(R.layout.fragment_pop_up, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = binding.root

        resultText = binding.resultsText

        if(letterId != null) {
            resultText.text = letterId
            Log.d("fragment", letterId)
        }

        val buttonY: Button =binding.YesButton
        val buttonN: Button =binding.NoButton

        buttonY.setOnClickListener{
            val action = PopUpFragmentDirections.actionPopUpFragmentToSheetFragment()

            view.findNavController().navigate(action)
        }

        buttonN.setOnClickListener{
            val action = PopUpFragmentDirections.actionPopUpFragmentToCameraFragment()
            view.findNavController().navigate(action)
        }
    }

    companion object {

    }
}