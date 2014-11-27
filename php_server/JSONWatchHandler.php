<?php

include_once 'JSONProtocol.php';


class JSONWatchHandler extends JSONHandler {
	private $timeOut;
	private $pollInterval;

	public function __construct() {
		$this->timeOut = 60 * 1; // x minutes
		$this->pollInterval = 1;
	}

	public function call($jsonArray, $jsonId) {
		if (strcmp($jsonArray['method'], "watchBranches") != 0)
			return false;
		$params = $jsonArray["params"];
		if ($params === null)
			return false;

		if (!isset($params['branches']))
			return false;
		$branches = $params['branches'];

		$branchToTipMap = array();
		foreach ($branches as $branchInfo) { 
			$branchToTipMap[$branchInfo['branch']] = $branchInfo['tip'];
		}

		// watch
		$database = Session::get()->getDatabase(Session::get()->getAccountUser());
		if ($database === null)
			throw new exception("unable to get database");

		// allow other requests from the same client to get through, e.g., to post messages
		session_write_close();

		$updatedBranches = array();

		$response = "serverTimeout";
		$startTime = time();
		$diff = 0;
		while ($diff < $this->timeOut) {
			foreach ($branchToTipMap as $branch => $tip) {
				try {
					$currentTip = sha1_hex($database->getTip($branch));
				} catch (exception $e) {
					// branch is not there so trigger a sync by returning an invalid tip
					$currentTip = "DoesNotExist";
				}
				if ($currentTip != $tip)
					$updatedBranches[] = $branch;
			}
			if (count($updatedBranches) > 0) {
				$response = "update";
				break;
			}
			sleep($this->pollInterval);
			$diff = time() - $startTime;
		}

		// reply
		$argumentArray = array('status' => 0, 'message' => "", 'response' => $response);
		if (count($updatedBranches) > 0)
			$argumentArray['branches'] = $updatedBranches;
		return $this->makeJSONRPCReturn($jsonId, $argumentArray);
	}
}

?>