"""Builds app/src/main/assets/spanish_cards.json, the bundled Spanish course.

Cards are (spanish, english, note) per level. Every card here is original,
hand-written teaching material (the proverbs are traditional sayings). Edit the
lists below and re-run:  python tools/build_spanish.py
"""
import json
import pathlib

L1 = [  # Beginner: single words, always with the article so gender sticks
    ("la casa", "the house", "Feminine: la casa"),
    ("el agua", "the water", "Feminine, but takes el before the stressed a"),
    ("el pan", "the bread", "Masculine"),
    ("la mesa", "the table", "Feminine"),
    ("el libro", "the book", "Masculine"),
    ("la ciudad", "the city", "Nouns ending in -dad are feminine"),
    ("el amigo", "the friend (male)", "La amiga for a female friend"),
    ("la familia", "the family", "Feminine"),
    ("el perro", "the dog", "Masculine; rr is rolled"),
    ("el gato", "the cat", "Masculine"),
    ("la leche", "the milk", "Feminine; ch as in chair"),
    ("el café", "the coffee", "The accent marks the stress"),
    ("la calle", "the street", "ll sounds like y"),
    ("el trabajo", "the work, the job", "j is a throaty h"),
    ("la escuela", "the school", "Spanish words never start with sc; they add e"),
    ("el coche", "the car", "In Latin America: el carro"),
    ("la comida", "the food, the meal", "Feminine"),
    ("el tiempo", "the time; the weather", "Context tells you which"),
    ("la noche", "the night", "Buenas noches = good night"),
    ("el día", "the day", "Masculine, despite ending in -a"),
    ("hola", "hello", "The h is silent"),
    ("adiós", "goodbye", "Stress on the last syllable"),
    ("gracias", "thank you", "Muchas gracias = thank you very much"),
    ("por favor", "please", "Literally: as a favour"),
    ("sí", "yes", "With an accent; si without means if"),
    ("no", "no", "Same as English"),
    ("buenos días", "good morning", "Used until about midday"),
    ("buenas tardes", "good afternoon", "From midday until dusk"),
    ("mucho gusto", "nice to meet you", "Literally: much pleasure"),
    ("perdón", "sorry, excuse me", "Also used to get past someone"),
    ("rojo", "red", "Adjectives change: roja for feminine nouns"),
    ("azul", "blue", "Same for masculine and feminine"),
    ("grande", "big", "Same for masculine and feminine"),
    ("pequeño", "small", "ñ sounds like ny"),
    ("bueno", "good", "Becomes buen before a masculine noun"),
    ("malo", "bad", "Becomes mal before a masculine noun"),
    ("uno", "one", "Uno, dos, tres, cuatro, cinco"),
    ("diez", "ten", "z sounds like th (Spain) or s (Latin America)"),
    ("hoy", "today", "Hoy es lunes = today is Monday"),
    ("mañana", "tomorrow; morning", "Context tells you which"),
]

L2 = [  # Elementary: survival phrases
    ("¿Cómo te llamas?", "What is your name?", "Note the ¿ at the start"),
    ("Me llamo Ana.", "My name is Ana.", "Literally: I call myself Ana"),
    ("¿Cómo estás?", "How are you?", "Formal: ¿Cómo está usted?"),
    ("Estoy bien, gracias.", "I am fine, thank you.", "estoy = I am (for states)"),
    ("¿Y tú?", "And you?", "tú with an accent means you"),
    ("Soy de México.", "I am from Mexico.", "ser for origin and identity"),
    ("No entiendo.", "I do not understand.", "Say it slowly: no en-TYEN-do"),
    ("¿Puede repetir, por favor?", "Can you repeat, please?", "Very useful early on"),
    ("Habla más despacio, por favor.", "Speak more slowly, please.", "despacio = slowly"),
    ("¿Hablas inglés?", "Do you speak English?", "The h is silent"),
    ("Hablo un poco de español.", "I speak a little Spanish.", "un poco de = a little"),
    ("¿Dónde está el baño?", "Where is the bathroom?", "estar for locations"),
    ("¿Dónde está la estación?", "Where is the station?", "Station = estación"),
    ("Quisiera un café, por favor.", "I would like a coffee, please.", "quisiera is the polite way to ask"),
    ("La cuenta, por favor.", "The bill, please.", "Said to the waiter"),
    ("¿Cuánto cuesta?", "How much does it cost?", "cuesta from costar"),
    ("Es muy caro.", "It is very expensive.", "barato = cheap"),
    ("Tengo hambre.", "I am hungry.", "Spanish says: I have hunger"),
    ("Tengo sed.", "I am thirsty.", "Spanish says: I have thirst"),
    ("Tengo frío.", "I am cold.", "Also: tengo calor = I am hot"),
    ("Me gusta la música.", "I like music.", "Literally: music pleases me"),
    ("No me gusta el pescado.", "I do not like fish.", "Add no in front of me gusta"),
    ("¿Qué hora es?", "What time is it?", "Answer: Son las tres = it is three"),
    ("Es la una.", "It is one o'clock.", "One o'clock is singular: es la una"),
    ("Nos vemos mañana.", "See you tomorrow.", "Literally: we see each other tomorrow"),
    ("Hasta luego.", "See you later.", "Literally: until later"),
    ("Buen viaje.", "Have a good trip.", "viaje = trip, journey"),
    ("Feliz cumpleaños.", "Happy birthday.", "cumpleaños = birthday"),
    ("¿Qué tal?", "How is it going?", "Casual greeting"),
    ("Mucho gusto en conocerte.", "Nice to meet you.", "conocer = to meet, to know a person"),
    ("Necesito ayuda.", "I need help.", "ayuda = help"),
    ("¿Me puede ayudar?", "Can you help me?", "Polite form"),
    ("Está bien.", "It is fine.", "Also means okay"),
    ("De nada.", "You are welcome.", "Literally: of nothing"),
    ("Lo siento mucho.", "I am very sorry.", "Stronger than perdón"),
    ("Con permiso.", "Excuse me.", "Used when passing through"),
    ("¿Cuántos años tienes?", "How old are you?", "Spanish says: how many years do you have"),
    ("Tengo veinte años.", "I am twenty years old.", "Always tener for age"),
    ("Vivo en una ciudad pequeña.", "I live in a small city.", "vivir = to live"),
    ("Mi casa es tu casa.", "My home is your home.", "A warm host's saying"),
]

L3 = [  # Intermediate: full sentences with past and future, plus core verbs
    ("Ayer fuimos al mercado y compramos fruta.", "Yesterday we went to the market and bought fruit.", "fuimos = ir, past; compramos = comprar, past"),
    ("Mañana voy a visitar a mi abuela.", "Tomorrow I am going to visit my grandmother.", "ir a + infinitive = near future"),
    ("Cuando era niño, jugaba en el parque.", "When I was a child, I used to play in the park.", "jugaba is the imperfect: habitual past"),
    ("Si tengo tiempo, te llamaré esta noche.", "If I have time, I will call you tonight.", "llamaré is the future tense"),
    ("Estaba cansado, así que me acosté temprano.", "I was tired, so I went to bed early.", "así que = so, therefore"),
    ("¿Has estado alguna vez en Madrid?", "Have you ever been to Madrid?", "has estado = present perfect"),
    ("No sé dónde puse las llaves.", "I do not know where I put the keys.", "puse = poner, past"),
    ("Me gustaría viajar por toda España.", "I would like to travel all over Spain.", "me gustaría = conditional"),
    ("Mientras cocinaba, sonó el teléfono.", "While I was cooking, the phone rang.", "Imperfect for the background, past for the event"),
    ("Hace dos años que vivo aquí.", "I have been living here for two years.", "hace + time + que = for such a time"),
    ("Es importante que estudies todos los días.", "It is important that you study every day.", "estudies is the subjunctive"),
    ("Quiero que vengas conmigo.", "I want you to come with me.", "querer que + subjunctive"),
    ("Me dijeron que la película era buena.", "They told me the film was good.", "dijeron = decir, past"),
    ("Aunque llueva, saldremos a caminar.", "Even if it rains, we will go out walking.", "aunque + subjunctive = even if"),
    ("Después de comer, siempre tomo un café.", "After eating, I always have a coffee.", "después de + infinitive"),
    ("Tenemos que salir antes de las ocho.", "We have to leave before eight.", "tener que + infinitive = must"),
    ("Hay muchas cosas que quiero aprender.", "There are many things I want to learn.", "hay = there is, there are"),
    ("Vamos a la playa los domingos.", "We go to the beach on Sundays.", "los domingos = on Sundays"),
    ("Le di el regalo a mi hermana.", "I gave the present to my sister.", "le = indirect object (to her)"),
    ("Lo vi ayer en la estación.", "I saw him yesterday at the station.", "lo = direct object (him)"),
    ("tener", "to have", "tengo, tienes, tiene, tenemos, tienen"),
    ("ser", "to be (identity, origin)", "soy, eres, es, somos, son"),
    ("estar", "to be (state, place)", "estoy, estás, está, estamos, están"),
    ("ir", "to go", "voy, vas, va, vamos, van"),
    ("hacer", "to do, to make", "hago, haces, hace, hacemos, hacen"),
    ("poder", "to be able to, can", "puedo, puedes, puede, podemos, pueden"),
    ("querer", "to want, to love", "quiero, quieres, quiere, queremos, quieren"),
    ("decir", "to say, to tell", "digo, dices, dice, decimos, dicen"),
    ("saber", "to know (a fact)", "sé, sabes, sabe, sabemos, saben"),
    ("conocer", "to know (a person or place)", "conozco, conoces, conoce, conocemos, conocen"),
    ("venir", "to come", "vengo, vienes, viene, venimos, vienen"),
    ("dar", "to give", "doy, das, da, damos, dan"),
    ("ver", "to see", "veo, ves, ve, vemos, ven"),
    ("pensar", "to think", "pienso, piensas, piensa, pensamos, piensan"),
    ("salir", "to go out, to leave", "salgo, sales, sale, salimos, salen"),
]

L4 = [  # Advanced: tougher words, false friends, proverbs (traditional sayings)
    ("aprovechar", "to take advantage of, to make the most of", "¡Que aproveche! = enjoy your meal"),
    ("desarrollar", "to develop", "el desarrollo = development"),
    ("lograr", "to achieve, to manage to", "lograr + infinitive"),
    ("a pesar de", "despite, in spite of", "a pesar de que + verb = although"),
    ("sin embargo", "however, nevertheless", "Formal connector"),
    ("por lo tanto", "therefore", "Formal connector"),
    ("echar de menos", "to miss (someone)", "Me haces falta is another way to say it"),
    ("tardar en", "to take time to", "Tardo una hora en llegar = it takes me an hour to arrive"),
    ("darse cuenta de", "to realize", "Me di cuenta de que... = I realized that..."),
    ("enterarse de", "to find out about", "Me enteré de la noticia = I heard the news"),
    ("el desafío", "the challenge", "Also: el reto"),
    ("el ambiente", "the atmosphere; the environment", "medio ambiente = environment"),
    ("la sobremesa", "chatting at the table after a meal", "No exact English word"),
    ("estrenar", "to use or wear for the first time", "estrenar zapatos = to wear new shoes for the first time"),
    ("madrugar", "to get up early", "Related: la madrugada = the early hours"),
    ("sobrar", "to be left over, to be more than enough", "Sobra comida = there is food left over"),
    ("actualmente", "currently (not actually)", "False friend: actually = en realidad"),
    ("realizar", "to carry out (not to realize)", "False friend: to realize = darse cuenta"),
    ("la librería", "the bookshop (not library)", "False friend: library = la biblioteca"),
    ("embarazada", "pregnant (not embarrassed)", "False friend: embarrassed = avergonzado"),
    ("el éxito", "success (not exit)", "False friend: exit = la salida"),
    ("la carpeta", "the folder (not carpet)", "False friend: carpet = la alfombra"),
    ("constipado", "having a cold (not constipated)", "False friend, common in Spain"),
    ("Más vale tarde que nunca.", "Better late than never.", "Traditional proverb"),
    ("A caballo regalado no se le mira el diente.", "Do not look a gift horse in the mouth.", "Traditional proverb"),
    ("Al mal tiempo, buena cara.", "Face bad times with a smile.", "Traditional proverb"),
    ("Camarón que se duerme, se lo lleva la corriente.", "The shrimp that falls asleep is carried off by the current.", "Traditional proverb: stay alert"),
    ("Dime con quién andas y te diré quién eres.", "Tell me who you walk with and I will tell you who you are.", "Traditional proverb"),
    ("En boca cerrada no entran moscas.", "Flies do not enter a closed mouth.", "Traditional proverb: silence is wise"),
    ("Más sabe el diablo por viejo que por diablo.", "The devil knows more from age than from being the devil.", "Traditional proverb: experience counts"),
    ("No hay mal que por bien no venga.", "Every cloud has a silver lining.", "Traditional proverb"),
    ("Quien mucho abarca, poco aprieta.", "He who grasps at too much holds little.", "Traditional proverb"),
    ("Del dicho al hecho hay mucho trecho.", "Saying and doing are far apart.", "Traditional proverb"),
    ("Poco a poco se va lejos.", "Little by little you go far.", "Traditional proverb"),
]


def kind3(es):
    return "verb" if " " not in es else "sentence"


def kind4(note):
    if note.startswith("Traditional proverb"):
        return "proverb"
    if note.startswith("False friend"):
        return "false friend"
    return "tough word"


# Every single word, verb and tough word carries a Spanish example sentence and its translation.
# Phrases, sentences and proverbs are already sentences, so they have none.
EXAMPLES = {
    "la casa": ("Mi casa tiene un jardín pequeño.", "My house has a small garden."),
    "el agua": ("Quiero un vaso de agua fría.", "I want a glass of cold water."),
    "el pan": ("Compramos pan fresco cada mañana.", "We buy fresh bread every morning."),
    "la mesa": ("El libro está sobre la mesa.", "The book is on the table."),
    "el libro": ("Este libro es muy interesante.", "This book is very interesting."),
    "la ciudad": ("Madrid es una ciudad grande.", "Madrid is a big city."),
    "el amigo": ("Mi amigo vive cerca de aquí.", "My friend lives near here."),
    "la familia": ("Mi familia cena junta los domingos.", "My family has dinner together on Sundays."),
    "el perro": ("El perro corre por el parque.", "The dog runs through the park."),
    "el gato": ("El gato duerme en el sofá.", "The cat sleeps on the sofa."),
    "la leche": ("Me gusta el café con leche.", "I like coffee with milk."),
    "el café": ("Tomo un café por la mañana.", "I have a coffee in the morning."),
    "la calle": ("Mi calle es muy tranquila.", "My street is very quiet."),
    "el trabajo": ("Mi trabajo empieza a las nueve.", "My work starts at nine."),
    "la escuela": ("Los niños van a la escuela en autobús.", "The children go to school by bus."),
    "el coche": ("Mi padre compró un coche nuevo.", "My father bought a new car."),
    "la comida": ("La comida de mi abuela es deliciosa.", "My grandmother's food is delicious."),
    "el tiempo": ("No tengo tiempo para salir hoy.", "I do not have time to go out today."),
    "la noche": ("Por la noche leo un poco.", "At night I read a little."),
    "el día": ("Hoy es un día muy bonito.", "Today is a very beautiful day."),
    "hola": ("Hola, ¿cómo estás?", "Hello, how are you?"),
    "adiós": ("Adiós, nos vemos mañana.", "Goodbye, see you tomorrow."),
    "gracias": ("Gracias por tu ayuda.", "Thank you for your help."),
    "por favor": ("Un café, por favor.", "A coffee, please."),
    "sí": ("Sí, quiero ir contigo.", "Yes, I want to go with you."),
    "no": ("No, gracias, ya comí.", "No, thank you, I already ate."),
    "buenos días": ("Buenos días, señora López.", "Good morning, Mrs López."),
    "buenas tardes": ("Buenas tardes, ¿en qué puedo ayudarle?", "Good afternoon, how can I help you?"),
    "mucho gusto": ("Mucho gusto, me llamo Pablo.", "Nice to meet you, my name is Pablo."),
    "perdón": ("Perdón, ¿dónde está la estación?", "Excuse me, where is the station?"),
    "rojo": ("Ella lleva un vestido rojo.", "She is wearing a red dress."),
    "azul": ("El cielo está muy azul hoy.", "The sky is very blue today."),
    "grande": ("Vivimos en una casa grande.", "We live in a big house."),
    "pequeño": ("Tengo un perro pequeño.", "I have a small dog."),
    "bueno": ("Este pan está muy bueno.", "This bread is very good."),
    "malo": ("Esta idea no es mala.", "This idea is not bad."),
    "uno": ("Uno, dos, tres: ¡vamos!", "One, two, three: let's go!"),
    "diez": ("Son las diez de la noche.", "It is ten at night."),
    "hoy": ("Hoy voy al mercado.", "Today I am going to the market."),
    "mañana": ("Mañana tengo una reunión importante.", "Tomorrow I have an important meeting."),
    "tener": ("Tengo dos hermanos.", "I have two brothers."),
    "ser": ("Mi madre es profesora.", "My mother is a teacher."),
    "estar": ("Estamos en casa ahora.", "We are at home now."),
    "ir": ("Voy al trabajo en tren.", "I go to work by train."),
    "hacer": ("Hacemos la cena juntos.", "We make dinner together."),
    "poder": ("¿Puedes abrir la ventana?", "Can you open the window?"),
    "querer": ("Quiero aprender español.", "I want to learn Spanish."),
    "decir": ("Ella dice la verdad.", "She tells the truth."),
    "saber": ("No sé la respuesta.", "I do not know the answer."),
    "conocer": ("Conozco bien esta ciudad.", "I know this city well."),
    "venir": ("Mis primos vienen el sábado.", "My cousins are coming on Saturday."),
    "dar": ("Te doy mi número de teléfono.", "I give you my phone number."),
    "ver": ("Veo a mis amigos los viernes.", "I see my friends on Fridays."),
    "pensar": ("Pienso en mis vacaciones.", "I am thinking about my holidays."),
    "salir": ("Salimos de casa a las ocho.", "We leave home at eight."),
    "aprovechar": ("Hay que aprovechar el buen tiempo.", "We must make the most of the good weather."),
    "desarrollar": ("La empresa quiere desarrollar un nuevo producto.", "The company wants to develop a new product."),
    "lograr": ("Logró terminar el proyecto a tiempo.", "He managed to finish the project on time."),
    "a pesar de": ("A pesar de la lluvia, salimos a pasear.", "Despite the rain, we went out for a walk."),
    "sin embargo": ("Estudié mucho; sin embargo, no aprobé.", "I studied a lot; however, I did not pass."),
    "por lo tanto": ("Es tarde; por lo tanto, nos vamos.", "It is late; therefore, we are leaving."),
    "echar de menos": ("Echo de menos a mi familia.", "I miss my family."),
    "tardar en": ("Tardamos una hora en llegar al centro.", "It takes us an hour to get downtown."),
    "darse cuenta de": ("Me di cuenta de mi error demasiado tarde.", "I realized my mistake too late."),
    "enterarse de": ("Me enteré de la noticia por la radio.", "I found out about the news on the radio."),
    "el desafío": ("Aprender un idioma es un gran desafío.", "Learning a language is a great challenge."),
    "el ambiente": ("En este café hay muy buen ambiente.", "This café has a very nice atmosphere."),
    "la sobremesa": ("Después de comer, nos quedamos de sobremesa.", "After eating, we stayed chatting at the table."),
    "estrenar": ("Hoy voy a estrenar mis zapatos nuevos.", "Today I am going to wear my new shoes for the first time."),
    "madrugar": ("Mañana tengo que madrugar.", "Tomorrow I have to get up early."),
    "sobrar": ("Sobró mucha comida en la fiesta.", "A lot of food was left over at the party."),
    "actualmente": ("Actualmente vivo en Sevilla.", "I currently live in Seville."),
    "realizar": ("Vamos a realizar un estudio pequeño.", "We are going to carry out a small study."),
    "la librería": ("Compré este libro en la librería del barrio.", "I bought this book at the neighbourhood bookshop."),
    "embarazada": ("Mi hermana está embarazada de seis meses.", "My sister is six months pregnant."),
    "el éxito": ("El concierto fue un gran éxito.", "The concert was a great success."),
    "la carpeta": ("Guardé los documentos en una carpeta.", "I kept the documents in a folder."),
    "constipado": ("Estoy constipado y tengo tos.", "I have a cold and a cough."),
}


def card(level, kind, es, en, note):
    c = {"l": level, "k": kind, "es": es, "en": en, "n": note}
    if es in EXAMPLES:
        c["ex"], c["ext"] = EXAMPLES[es]
    return c


cards = []
for es, en, note in L1:
    cards.append(card(1, "word", es, en, note))
for es, en, note in L2:
    cards.append(card(2, "phrase", es, en, note))
for es, en, note in L3:
    cards.append(card(3, kind3(es), es, en, note))
for es, en, note in L4:
    cards.append(card(4, kind4(note), es, en, note))

# Single words, verbs and tough words must all have an example; sentences must not need one.
NEEDS_EXAMPLE = {"word", "verb", "tough word", "false friend"}
missing = [c["es"] for c in cards if c["k"] in NEEDS_EXAMPLE and "ex" not in c]
assert not missing, f"cards without an example: {missing}"

out = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/assets/spanish_cards.json"
out.write_text(json.dumps(cards, ensure_ascii=False, indent=0), encoding="utf-8")
print(f"wrote {len(cards)} cards to {out}")
