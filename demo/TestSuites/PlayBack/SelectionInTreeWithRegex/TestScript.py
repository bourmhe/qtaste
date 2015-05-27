# encoding= utf-8

##
# Playback/Selection test.
# <p>
# Description of the test.
#
##

from qtaste import *

import time

# update in order to cope with the javaGUI extension declared in your testbed configuration.
javaguiMI = testAPI.getJavaGUI(INSTANCE_ID=testData.getValue("JAVAGUI_INSTANCE_NAME"))
subtitler = testAPI.getSubtitler()

importTestScript("TabbedPaneSelection")

def step1():
    """
    @step      Description of the actions done for this step
    @expected  Description of the expected result
    """

    doSubSteps(TabbedPaneSelection.changeTabById)
    subtitler.setSubtitle(testData.getValue("COMMENT"))

    component = testData.getValue("COMPONENT_NAME")
    value = testData.getValue("VALUE")

    javaguiMI.clearNodeSelection(component)

    javaguiMI.selectNodeRe(component, value, "!")
    actualSelection = javaguiMI.getSelectedNode(component, "!")

    if actualSelection is None:
        testAPI.stopTest(Status.FAIL, "Unable to get the selected node. No node is selected.")

    time.sleep(1)


doStep(step1)
