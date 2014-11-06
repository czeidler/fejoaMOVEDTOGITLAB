<?php

include_once 'Profile.php';


class Transaction {
	private $timeStamp;
	private $uid;

	public function __construct() {
		$this->timeStamp = time();
		$this->uid = Session::get()->newTransactionId();
	}

	public function getUid() {
		return $this->uid;
	}

	public function getTimeStamp() {
		return $this->timeStamp;
	}
}

class Session {
	public static function get() {
		static $sSession = null;
		if ($sSession === NULL)
			$sSession = new Session();
		return $sSession;
	}

	public function clear() {
		$this->setSignatureToken("");
		$this->setUserRoles(array());
	}

	public function getUserDir($user) {
		return $user;
	}

	public function getDatabase($user) {
		$accountUser = Session::get()->getAccountUser();
		if ($user == $accountUser) {
			if (!file_exists($user))
				mkdir($user);
		} else {
			if (!file_exists($user))
				return null;
		}
		$database = new GitDatabase($user."/.git");

		$databasePath = $user."/.git";
		if (!file_exists($databasePath))
			return null;
		return new GitDatabase($databasePath);
	}

	public function getProfile($serverUser) {
		$database = $this->getDatabase($serverUser);
		if ($database === null)
			return null;
		return new Profile($database, "profile", "");
	}

	public function getMainMailbox($serverUser) {
		$profile = $this->getProfile($serverUser);
		if ($profile === null)
			return null;
		return $profile->getMainMailbox();
	}

	public function getMainUserIdentity($serverUser) {
		$profile = $this->getProfile($serverUser);
		if ($profile === null)
			return null;
		return $profile->getUserIdentityAt(0);
	}

	public function newTransactionId() {
		$transactionId = 0;
		if (isset($_SESSION['transaction_id']))
			$transactionId = $_SESSION['transaction_id'];
		$newId = $transactionId + 1;
		$_SESSION['transaction_id'] = $newId;
		return $transactionId;
	}
	
	public function getAccountUser() {
		if (!isset($_SESSION['account_user']))
			return "";
		return $_SESSION['account_user'];
	}
	
	public function setAccountUser($user) {
		$_SESSION['account_user'] = $user;
	}

	public function addTransaction($transaction) {
		if (!isset($_SESSION['transactions']))
			$_SESSION['transactions'] = array();
		$_SESSION['transactions'][$transaction->getUid()] = serialize($transaction);
	}

	public function getTransaction($transactionId) {
		if (!isset($_SESSION['transactions'][$transactionId]))
			return null;
		return unserialize($_SESSION['transactions'][$transactionId]);
	}

	public function removeTransaction($transaction) {
		unset($_SESSION['transactions'][$transaction->getUid()]);
	}

	public function addBranchAccess($branchAccessToken) {
		if (!isset($_SESSION['branchAccess']))
			$_SESSION['branchAccess'] = array();
		else if ($this->hasBranchAccess($branchAccessToken))
			return;
		$_SESSION['branchAccess'][] = $branchAccessToken;
	}

	public function hasBranchAccess($branchAccessToken) {
		if (!isset($_SESSION['branchAccess']))
			return false;
		return in_array($branchAccessToken, $_SESSION['branchAccess']);
	}

	public function setUserRoles($roles) {
		$_SESSION['user_roles'] = $roles;
	}
	
	public function getUserRoles() {
		if (!isset($_SESSION['user_roles']))
			return array();
		return $_SESSION['user_roles'];
	}

	public function isAccountUser() {
		return in_array("account", $this->getUserRoles());
	}
}

?>
