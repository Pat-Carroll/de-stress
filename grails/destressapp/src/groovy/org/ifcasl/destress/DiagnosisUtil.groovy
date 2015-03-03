package org.ifcasl.destress

class DiagnosisUtil {

    static Diagnosis getDiagnosis(Exercise ex, WordUtterance studUtt, List refUtts) {
        def scorer = ex.diagnosisMethod.scorer
        def fbc

        // Get 3 scores
        def durScore
        def f0Score
        def intScore
        if (scorer.useJsnooriScores) {
            if (refUtts.size()==1) {
                def refUtt = refUtts.get(0)

                // get scores from FeedbackComputer
                fbc = JsnooriBridge.getFeedbackComputer(ex, studUtt, refUtt)
                durScore = fbc.timeFeedback.getScore()
                f0Score = fbc.pitchFeedback.getPitchScore()
                intScore = fbc.energyFeedback.getEnergyScore()


            }
            else { // not exactly 1 reference
                throw new Exception("I can only handle 1 reference utterance at the moment, and you've given me " +  refUtts.size().toString())
                }
        }
        else { //not using Jsnoori scores
            throw new Exception("I can only handle Jsnoori scores at the moment, and the scorer " + scorer.toString() + " doesn't use them")
        }


        // Get overall score
        def durWt = scorer.durationWeight
        def f0Wt = scorer.f0Weight
        def intWt = scorer.intensityWeight
        def allScore = new Float(durScore*durWt + f0Score*f0Wt + intScore*intWt)

        // Create diagnosis object
        def diag = new Diagnosis(exercise:ex,
                                 studentUtterance:studUtt,
                                 referenceUtterances:refUtts,
                                 durationScore:durScore,
                                 f0Score:f0Score,
                                 intensityScore:intScore,
                                 overallScore:allScore,
                                 )

        // save feedbackSignal to file
        def waveName = diag.toString() + ".wav"
        println ("waveName: " + waveName)
        def grailsApplication = new Diagnosis().domainClass.grailsApplication
        def feedbackPath = grailsApplication.mainContext.servletContext.getRealPath("/") + "audio/feedback/" + waveName
        diag.feedbackWaveFile = waveName
        fbc.feedbackSignal.saveWave(new File(feedbackPath))

        // save & return
        diag.save()
        return diag
    }

    static String getColor(Float score) {
        def col
        if (score < 0.25) {
            col = "red"
        } else if (score < 0.7) {
            col = "yellow"
        } else {
            col = "green"
        }
        return col
    }

}