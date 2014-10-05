<?php

include_once 'Session.php';


class JSONHandler {
	public function call($jsonArray, $jsonRPCId) {
		return false;
	}

	protected function makeJSONRPCReturn($id, $result) {
		$array = array('jsonrpc' => "2.0", 'result' => $result, 'id' => $id);
		return json_encode($array);
	}

	protected function makeError($jsonId, $message) {
		return $this->makeJSONRPCReturn($jsonId, array('status' => -1, 'message' => $message));
	}
}


class JSONDispatcher {
	private $handlers = array();

	public function addHandler($jsonHandler) {
		$this->handlers[] = $jsonHandler;
	}

	public function dispatch($json) {
		$jsonArray = json_decode($json, true);

		if ($jsonArray === null)
			return false;
			
		if (!isset($jsonArray['method']))
			return false;
		if (!isset($jsonArray['id']))
			return false;

		foreach ($this->handlers as $handler) {
			$response = $handler->call($jsonArray, $jsonArray['id']);
			if ($response !== false)
				return $response;
		}

		return false;
	}
}

?>
