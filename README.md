# CharacterSheet
Repository for my Honour's project "integrating physcial dice in a virtual game enviroment".
The application was built in Android Studio using Kotlin.
It makes use of 2 computer vision models to extract the result of a dice contained within an image.
Model 1) an object detection model which can identify the top face of a dice.
Model 2) MLKit's text recognition model provided by google was used to determine the number contained within the top face detected by the first model.
The result is then sent as a string to an I.P address. 
