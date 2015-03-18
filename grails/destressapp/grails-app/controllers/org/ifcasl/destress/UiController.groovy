package org.ifcasl.destress

class UiController {


    def index() {
        def id = params['id']
        def ex = Exercise.get(id)
        def cFG = WordUtterance.createCriteria()
        //def fgUtts = WordUtterance.findAllByWord(ex.word)
        def fgUtts = cFG {
            and {
                eq("word",ex.word)
                sentenceUtterance {
                    speaker {
                        eq("nativeLanguage", Language.F)
                    }
                }
            }
        }

        def ggUtts
        def nRefs = ex.diagnosisMethod.numberOfReferences
        if (nRefs > 0) {
            if (ex.diagnosisMethod.selectionType == SelectionType.MANUAL) {

                // def refType = ex.diagnosisMethod.referenceType
                // if (refType!=ReferenceType.ABSTRACT)

                if (nRefs > 0) {
                    def cGG = WordUtterance.createCriteria()
                    ggUtts = cGG {
                        and {
                            eq("word",ex.word)
                            sentenceUtterance {
                                speaker {
                                    eq("nativeLanguage", Language.G)
                                }
                            }
                        }
                    }
                }
            }
        }


        render(view:"exercise", model:[ex:ex,
                                       fgUtts:fgUtts,
                                       ggUtts:ggUtts,
                                       nRefs:(1..nRefs)])
    }

    def record() {
    }

    def upload() {

    }

    def download(long id) {
        SentenceUtterance utt = SentenceUtterance.get(id)
        File file = new File(utt.waveFile)
        response.setContentType('audio/wav')
        response.setContentLength (file.bytes.length)
        response.setHeader("Content-disposition", "attachment;filename=${file.getName()}")
        response.outputStream << file.newInputStream() // Performing a binary stream copy

        return false
    }

    def downloadFeedback(long id) {
        Diagnosis diag = Diagnosis.get(id)
        def path = grailsApplication.mainContext.servletContext.getRealPath("/") + "audio/feedback/" + diag.feedbackWaveFile
        File file = new File(path)
        response.setContentType('audio/wav')
        response.setContentLength (file.bytes.length)
        response.setHeader("Content-disposition", "attachment;filename=${file.getName()}")
        response.outputStream << file.newInputStream() // Performing a binary stream copy

        return false
    }

    def selfassess() {
        def ex = Exercise.get(params['id'])
        def studUtt = WordUtterance.get(params['fgUtts.id'])
        def studWav = studUtt.sentenceUtterance.sampleName + ".wav"

        def refUtts = getRefUtts(ex, studUtt)

        [ex:ex,studUtt:studUtt,refUtts:refUtts,studWav:studWav]

    }

    def List getRefUtts(Exercise ex, WordUtterance studUtt) {
        def refUtts = []
        def nRefs = ex.diagnosisMethod.numberOfReferences
        println("PARAMS: " + params)
        if (nRefs > 0) {
            if (ex.diagnosisMethod.selectionType == SelectionType.AUTO) {
                refUtts = DiagnosisUtil.findNBestRefUtts(studUtt, nRefs)
            }
            else if (ex.diagnosisMethod.selectionType == SelectionType.MANUAL) {
                for (i in 1..nRefs) {
                    def refUtt = WordUtterance.get(params['ggUtts.' + i])
                    refUtts.add(refUtt)
                }
            }
            else { // selectionType == FIXED
                render("FIXED selectionType not yet implemented!")
            }
        }
        return refUtts
    }

    def feedback() {
        def ex = Exercise.get(params['id'])

        //def basePath = grailsApplication.mainContext.servletContext.getRealPath("/web-app/")

        def studUtt = WordUtterance.get(params['fgUtts.id'])
        def studWav = studUtt.sentenceUtterance.sampleName + ".wav"

        def refUtts = []
        //def refWavs = []
        def nRefs = ex.diagnosisMethod.numberOfReferences

        def diag

        if (nRefs > 0) {
            refUtts = getRefUtts(ex, studUtt)
            println("REFUTTS: " + refUtts)

            ///// Moved to getRefUtts()
            // if (ex.diagnosisMethod.selectionType == SelectionType.AUTO) {
            //     refUtts = DiagnosisUtil.findNBestRefs(studUtt.sentenceUtterance.speaker, nRefs)
            // }
            // else if (ex.diagnosisMethod.selectionType == SelectionType.MANUAL) {
            //
            //
            //     for (i in 1..nRefs) {
            //         def refUtt = WordUtterance.get(params['ggUtts.' + i])
            //         //def refWav = grailsApplication.parentContext.getResource("/audio/" + refUtt.sentenceUtterance.sampleName + ".wav")
            //         //def refWav = refUtt.sentenceUtterance.sampleName + ".wav"
            //
            //         //TODO validate that input from multiple refUtts selects are not the same utterance
            //
            //         refUtts.add(refUtt)
            //         //refWavs.add(refWav)
            //     }
            // }
            // else { // selectionType == FIXED
            //     render("FIXED selectionType not yet implemented!")
            // }

            diag = DiagnosisUtil.getComparisonDiagnosis(ex, studUtt, refUtts)
        }
        else { //nRefs <= 0
            //WekaUtil.getInstance(studUtt)
            //render("Weka diag not implemented yet")

            diag = DiagnosisUtil.getClassificationDiagnosis(ex, studUtt)

        }


        //TODO set self-assessment info
        diag.selfAssessedStressLabel = params['stressLabel']
        diag.selfAssessedStressClarity = params['clearEnough']
        diag.selfAssessedAdvice = params['advice']
        diag.save()
        println("selfAssessedStressLabel: " + diag.selfAssessedStressLabel)
        println("selfAssessedStressClarity: " + diag.selfAssessedStressClarity)
        println("selfAssessedAdvice: " + diag.selfAssessedAdvice)


        // get label color & message
        def labelCol
        def labelMsg
        if (diag.label) {
            labelCol = DiagnosisUtil.getColor(diag.label)
            if (ex.feedbackMethod.displayMessage) {
                labelMsg = DiagnosisUtil.getClassificationMessage(diag)
            }
        }


        // get skill scores, colors, & messages
        def durPct
        def f0Pct
        def intPct
        def allPct
        def durCol
        def f0Col
        def intCol
        def allCol
        def durMsg
        def f0Msg
        def intMsg


        if (ex.feedbackMethod.showSkillBars) {
            durPct = new Float(diag.durationScore * 100f)
            f0Pct = new Float(diag.f0Score * 100f)
            intPct = new Float(diag.intensityScore * 100f)
            allPct = new Float(diag.overallScore * 100f)
            durCol = DiagnosisUtil.getColor(diag.durationScore)
            f0Col = DiagnosisUtil.getColor(diag.f0Score)
            intCol = DiagnosisUtil.getColor(diag.intensityScore)
            allCol = DiagnosisUtil.getColor(diag.overallScore)

        }

        if (ex.feedbackMethod.displayMessage) {
            durMsg = DiagnosisUtil.getDurationMessage(diag)
            f0Msg = DiagnosisUtil.getF0Message(diag)
            intMsg = DiagnosisUtil.getIntensityMessage(diag)
            println("MESSAGES: ")
            println("durMsg: " + durMsg)
            println("f0Msg: " + f0Msg)
            println("intMsg: " + intMsg)
        }

        // get feedback audio
        def fbWav
        if (ex.feedbackMethod.playFeedbackSignal) {
            fbWav = diag.feedbackWaveFile // might be null
        }
        println("fbWav: " + fbWav)


        /// get syllable font sizes for studUtt
        def s0Pct = studUtt.SYLL0_DUR / studUtt.WORD_DUR
        def s1Pct = studUtt.SYLL1_DUR / studUtt.WORD_DUR
        println("studUtt: " + studUtt + "\ts0Pct: " + s0Pct + "\ts1Pct: " + s1Pct)
        def studSyllDurs = [s0Pct.round(2), s1Pct.round(2)]
        def s0Size = s0Pct * 3
        def s1Size = s1Pct * 3
        def studSyllSizes = [s0Size, s1Size]

        /// get mean-normalized F0
        println("studUtt: " + studUtt + "\tFeatures:")
        FeatureUtil.printWordUtteranceFeatures(studUtt)
        def s0F0 = (studUtt.SYLL0_F0_MEAN / studUtt.WORD_F0_MEAN).round(2)
        def s1F0 = (studUtt.SYLL1_F0_MEAN / studUtt.WORD_F0_MEAN).round(2)
        def studSyllF0s = [s0F0, s1F0]
        println("studUtt: " + studUtt + "\tstudSyllF0s: " + studSyllF0s)

        //// get mean-normalized intensity
        // println("studUtt.SYLL0_ENERGY_MEAN: " + studUtt.SYLL0_ENERGY_MEAN)
        // println("studUtt.WORD_ENERGY_MEAN: " + studUtt.WORD_ENERGY_MEAN)
        // println("studUtt.SYLL0_ENERGY_MEAN / studUtt.WORD_ENERGY_MEAN:" + studUtt.SYLL0_ENERGY_MEAN / studUtt.WORD_ENERGY_MEAN)
        def s0Int = (studUtt.SYLL0_ENERGY_MEAN / studUtt.WORD_ENERGY_MEAN ).round(2)
        def s1Int = (studUtt.SYLL1_ENERGY_MEAN / studUtt.WORD_ENERGY_MEAN).round(2)
        def studSyllInts = [s0Int, s1Int]
        println("studUtt: " + studUtt + "\tstudSyllInts: " + studSyllInts)


        /// get syllable sizes for refUtts
        def refSyllDurs = []
        def refSyllSizes = []
        def refSyllF0s = []
        def refSyllInts = []
        for (WordUtterance refUtt in refUtts) {
            /// get syllable font sizes
            // def r0Pct = refUtt.SYLL0_DUR / refUtt.WORD_DUR
            // def r1Pct = refUtt.SYLL1_DUR / refUtt.WORD_DUR
            // //println("refUtt: " + refUtt.toString() + "\ts0Pct: " + s0Pct + "\ts1Pct: " + s1Pct)
            // def r0Size = s0Pct * 3
            // def r1Size = s1Pct * 3

            println("refUtt: " + refUtt + "\tFeatures:")
            FeatureUtil.printWordUtteranceFeatures(refUtt)


            refSyllDurs.add([(refUtt.SYLL0_DUR/refUtt.WORD_DUR).round(2), (refUtt.SYLL1_DUR / refUtt.WORD_DUR).round(2)])

            //refSyllSizes.add([r0Size,r1Size])
            refSyllSizes.add([((refUtt.SYLL0_DUR/refUtt.WORD_DUR)*3),((refUtt.SYLL1_DUR / refUtt.WORD_DUR)*3)])
            println("refUtt: " + refUtt + "\trefSyllSizes: " + refSyllSizes[refUtts.indexOf(refUtt)])

            /// get syllable F0s
            refSyllF0s.add([(refUtt.SYLL0_F0_MEAN/refUtt.WORD_F0_MEAN).round(2), (refUtt.SYLL1_F0_MEAN/refUtt.WORD_F0_MEAN).round(2)])
            println("refUtt: " + refUtt + "\trefSyllF0s: " + refSyllF0s[refUtts.indexOf(refUtt)])

            /// get syllable Intensities

            refSyllInts.add([(refUtt.SYLL0_ENERGY_MEAN / refUtt.WORD_ENERGY_MEAN ).round(2),
                             (refUtt.SYLL1_ENERGY_MEAN / refUtt.WORD_ENERGY_MEAN).round(2)])
            println("refUtt: " + refUtt + "\trefSyllInts: " + refSyllF0s[refUtts.indexOf(refUtt)])
        }

        //// TODO get avg values for case where nRefs == 0



        //// TODO skill stuff
        def durStuff = ["Duration",durPct,durCol,durMsg]
        def f0Stuff = ["Pitch",f0Pct, f0Col, f0Msg]
        def intStuff = ["Loudness",intPct, intCol, intMsg]

        //render(view:"exercise", model:[ex:ex,fgUtts:[],ggUtts:[]])
        [ex:ex,diag:diag,
               studUtt:studUtt,
               refUtts:refUtts,
               studWav:studWav,
               fbWav:fbWav,
               //
               //durPct:durPct,durCol:durCol,durMsg:durMsg,
               //f0Pct:f0Pct,f0Col:f0Col,f0Msg:f0Msg,
               //intPct:intPct,intCol:intCol,intMsg:intMsg,
               durStuff:durStuff,
               f0Stuff:f0Stuff,
               intStuff:intStuff,
               //
               allPct:allPct,allCol:allCol,
               //
               studSyllSizes:studSyllSizes,
               refSyllSizes:refSyllSizes,
               studSyllDurs:studSyllDurs,
               refSyllDurs:refSyllDurs,
               studSyllF0s:studSyllF0s,
               refSyllF0s:refSyllF0s,
               studSyllInts:studSyllInts,
               refSyllInts:refSyllInts,
               labelCol:labelCol,
               labelMsg:labelMsg,
        ]
    }




}
