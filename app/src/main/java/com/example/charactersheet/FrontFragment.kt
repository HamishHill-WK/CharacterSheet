package com.example.charactersheet

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import com.example.charactersheet.databinding.FragmentFrontBinding
import java.io.PrintWriter
import java.net.Socket

//this fragment handles the main page of the application -hh

class FrontFragment : Fragment() {
    // TODO: Rename and change types of parameters
    private var _binding: FragmentFrontBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private lateinit var recyclerView: FrameLayout

    private var letterId: String? = null

    private lateinit var resultText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let {
            letterId = it.getString(RESULT).toString()
            Log.d("front frag", letterId!!)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        _binding = FragmentFrontBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recyclerView = binding.root

        resultText = binding.RollResult

        if(letterId != null) {
            resultText.text = letterId!!
            if(resultText.text != null)
                if(resultText.text.toString()  in (1..20).toString())
                Thread{connect(letterId.toString())}.start()
        }

        val button = binding.HitDieButton
        val button1 = binding.HealthButton

        button.setOnClickListener{
            Thread{connect()}.start()
        }

        button1.setOnClickListener{
            val action = FrontFragmentDirections.actionFrontFragmentToCameraFragment()
            view.findNavController().navigate(action)
        }

    }

    private fun connect() {
        //var message = resultText
        val client = Socket("192.168.0.14", 4000)
        val output = PrintWriter(client.getOutputStream(), true)
        //val input = BufferedReader(InputStreamReader(client.inputStream))

        val msg = "connect"

        //resultText = ClassificationResultsAdapter().getItemName()

        println(msg)
        output.println(msg)
        //println("Client receiving [${input.readLine()}]")
        client.close()

    }
    
    private fun connect(S: String){
        //var message = resultText
        val client = Socket("192.168.0.14", 4000)
        val output = PrintWriter(client.getOutputStream(), true)
        //val input = BufferedReader(InputStreamReader(client.inputStream))

        //resultText = ClassificationResultsAdapter().getItemName()

        println(S)
        output.println(S)
        //println("Client receiving [${input.readLine()}]")
        client.close()
    }

    companion object {
        val RESULT = "result"
    }
}