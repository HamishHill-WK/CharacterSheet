package com.example.charactersheet

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import com.example.charactersheet.databinding.FragmentFrontBinding
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

//this fragment handles the main page of the application -hh

class FrontFragment : Fragment() {
    private var _binding: FragmentFrontBinding? = null
    private var port: String = ""
    private var ipNum: String = ""

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

        if(letterId != null) {  //when the application returns to this fragment check if a value has been assigned to letterid
            resultText.text = letterId!!
            if(resultText.text.toString()  in (1..20).toString())   //if the result is a number between 1 and 20
            Thread{connect(letterId.toString())}.start()    //send number to server
        }

        val button = binding.ConnectButton
        val button1 = binding.HealthButton

        button.setOnClickListener{
            Thread{connect()}.start()
        }

        button1.setOnClickListener{
            val action = FrontFragmentDirections.actionFrontFragmentToCameraFragment()
            view.findNavController().navigate(action)
        }

        binding.ConnectionCode.addTextChangedListener (object : TextWatcher {

            override fun afterTextChanged(s: Editable?) {
               ipNum = s.toString()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                println("text touched")
            }
        })

        binding.ConnectionPort.addTextChangedListener (object : TextWatcher {

            override fun afterTextChanged(s: Editable?) {
               port = s.toString()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                println("text touched")
            }
        })
    }

    private fun connect() { //sends message to server at specified port
                            //this is a test function to check if a server exists
                            //server with DiceBot active will reply with discord server name the bot is connected to
        if(port != "" && ipNum != ""){ //check we have a port and ip number
            try {
                val client = Socket(ipNum, port.toInt())
                val output = PrintWriter(client.getOutputStream(), true)
                val input = BufferedReader(InputStreamReader(client.inputStream))
                output.println(0)
                var s = ""
                var search = true
                while(search) {
                    s += input.read().toChar()
                    if(s.endsWith(" SERVERNAMEEND"))    //this string is added by the server to the end of the discord server name
                        search = false                        //to prevent getting stuck reading the input stream
                }
                Toast.makeText(
                    requireContext(),
                    "server `$s ` found",
                    Toast.LENGTH_SHORT
                ).show()
                binding.ServerNameHolder.text = s.substringBefore(" SERVERNAMEEND")//string removed for assignment to UI textview
                client.close()
            } catch (exc: Exception) {//prevents crash if network connection fails
                Log.e(TAG, "connection failed", exc)
                Toast.makeText(
                    requireContext(),
                    "no server found, please try again",
                    Toast.LENGTH_SHORT
                ).show()
                binding.ServerNameHolder.text = "*no server found*"
            }
        }
    }
    
    private fun connect(S: String){ //sends message to server at specified port
        try {
            val client = Socket(ipNum, port.toInt())
            val output = PrintWriter(client.getOutputStream(), true)
            println(S)
            output.println(S)
            client.close()
            Toast.makeText(
                requireContext(),
                "sent $S to `${binding.ServerNameHolder.text} `",
                Toast.LENGTH_SHORT
            ).show()
        } catch (exc: Exception) {  //prevents crash if network connection fails
            Log.e(TAG, "connection failed", exc)
            Toast.makeText(
                requireContext(),
                "no server found, please try again",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    companion object {
        val TAG = "frontfrag"
        val RESULT = "result"
    }
}