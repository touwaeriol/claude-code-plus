/**
 * VS Code Git Extension Type Definitions
 * 
 * Based on vscode.git extension API
 * @see https://github.com/microsoft/vscode/blob/main/extensions/git/src/api/git.d.ts
 */

export interface GitExtension {
    readonly enabled: boolean;
    readonly onDidChangeEnablement: Event<boolean>;
    getAPI(version: 1): API;
}

export interface API {
    readonly state: 'uninitialized' | 'initialized';
    readonly onDidChangeState: Event<'uninitialized' | 'initialized'>;
    readonly onDidOpenRepository: Event<Repository>;
    readonly onDidCloseRepository: Event<Repository>;
    readonly repositories: Repository[];
    toGitUri(uri: Uri, ref: string): Uri;
    getRepository(uri: Uri): Repository | null;
}

export interface Repository {
    readonly rootUri: Uri;
    readonly inputBox: InputBox;
    readonly state: RepositoryState;
    readonly ui: RepositoryUIState;

    getConfigs(): Promise<{ key: string; value: string; }[]>;
    getConfig(key: string): Promise<string>;
    setConfig(key: string, value: string): Promise<string>;
    getGlobalConfig(key: string): Promise<string>;

    getObjectDetails(treeish: string, path: string): Promise<{ mode: string; object: string; size: number; }>;
    detectObjectType(object: string): Promise<{ mimetype: string; encoding?: string; }>;
    buffer(ref: string, path: string): Promise<Buffer>;
    show(ref: string, path: string): Promise<string>;
    getCommit(ref: string): Promise<Commit>;

    add(paths: Uri[]): Promise<void>;
    revert(paths: Uri[]): Promise<void>;
    clean(paths: Uri[]): Promise<void>;

    apply(patch: string, reverse?: boolean): Promise<void>;
    diff(cached?: boolean): Promise<string>;
    diffWithHEAD(): Promise<Change[]>;
    diffWith(ref: string): Promise<Change[]>;
    diffIndexWithHEAD(): Promise<Change[]>;
    diffIndexWith(ref: string): Promise<Change[]>;
    diffBlobs(object1: string, object2: string): Promise<string>;
    diffBetween(ref1: string, ref2: string): Promise<Change[]>;

    hashObject(data: string): Promise<string>;

    createBranch(name: string, checkout: boolean, ref?: string): Promise<void>;
    deleteBranch(name: string, force?: boolean): Promise<void>;
    getBranch(name: string): Promise<Branch>;
    getBranches(query: BranchQuery): Promise<Ref[]>;
    setBranchUpstream(name: string, upstream: string): Promise<void>;

    getRefs(query: RefQuery): Promise<Ref[]>;

    getMergeBase(ref1: string, ref2: string): Promise<string>;

    tag(name: string, upstream: string): Promise<void>;
    deleteTag(name: string): Promise<void>;

    status(): Promise<void>;
    checkout(treeish: string): Promise<void>;

    addRemote(name: string, url: string): Promise<void>;
    removeRemote(name: string): Promise<void>;
    renameRemote(name: string, newName: string): Promise<void>;

    fetch(remote?: string, ref?: string, depth?: number): Promise<void>;
    pull(unshallow?: boolean): Promise<void>;
    push(remoteName?: string, branchName?: string, setUpstream?: boolean, force?: ForcePushMode): Promise<void>;

    blame(path: string): Promise<string>;
    log(options?: LogOptions): Promise<Commit[]>;

    commit(message: string, opts?: CommitOptions): Promise<void>;
}

export interface InputBox {
    value: string;
}

export interface RepositoryState {
    readonly HEAD: Branch | undefined;
    readonly refs: Ref[];
    readonly remotes: Remote[];
    readonly submodules: Submodule[];
    readonly rebaseCommit: Commit | undefined;
    readonly mergeChanges: Change[];
    readonly indexChanges: Change[];
    readonly workingTreeChanges: Change[];
    readonly onDidChange: Event<void>;
}

export interface RepositoryUIState {
    readonly selected: boolean;
    readonly onDidChange: Event<void>;
}

export interface Branch extends Ref {
    readonly upstream?: UpstreamRef;
    readonly ahead?: number;
    readonly behind?: number;
}

export interface UpstreamRef {
    readonly remote: string;
    readonly name: string;
}

export interface Ref {
    readonly type: RefType;
    readonly name?: string;
    readonly commit?: string;
    readonly remote?: string;
}

export enum RefType {
    Head = 0,
    RemoteHead = 1,
    Tag = 2
}

export interface Remote {
    readonly name: string;
    readonly fetchUrl?: string;
    readonly pushUrl?: string;
    readonly isReadOnly: boolean;
}

export interface Submodule {
    readonly name: string;
    readonly path: string;
    readonly url: string;
}

export interface Commit {
    readonly hash: string;
    readonly message: string;
    readonly parents: string[];
    readonly authorDate?: Date;
    readonly authorName?: string;
    readonly authorEmail?: string;
    readonly commitDate?: Date;
}

export interface Change {
    readonly uri: Uri;
    readonly originalUri: Uri;
    readonly renameUri: Uri | undefined;
    readonly status: Status;
}

export enum Status {
    INDEX_MODIFIED = 0,
    INDEX_ADDED = 1,
    INDEX_DELETED = 2,
    INDEX_RENAMED = 3,
    INDEX_COPIED = 4,

    MODIFIED = 5,
    DELETED = 6,
    UNTRACKED = 7,
    IGNORED = 8,
    INTENT_TO_ADD = 9,
    INTENT_TO_RENAME = 10,
    TYPE_CHANGED = 11,

    ADDED_BY_US = 12,
    ADDED_BY_THEM = 13,
    DELETED_BY_US = 14,
    DELETED_BY_THEM = 15,
    BOTH_ADDED = 16,
    BOTH_DELETED = 17,
    BOTH_MODIFIED = 18,
}

export interface BranchQuery {
    readonly remote?: boolean;
    readonly pattern?: string;
    readonly count?: number;
    readonly contains?: string;
}

export interface RefQuery {
    readonly contains?: string;
    readonly count?: number;
    readonly pattern?: string;
    readonly sort?: 'alphabetically' | 'committerdate';
}

export interface LogOptions {
    readonly maxEntries?: number;
    readonly path?: string;
    readonly follow?: boolean;
    readonly sortByAuthorDate?: boolean;
    readonly reverse?: boolean;
}

export interface CommitOptions {
    readonly all?: boolean | 'tracked';
    readonly amend?: boolean;
    readonly signoff?: boolean;
    readonly signCommit?: boolean;
    readonly empty?: boolean;
    readonly noVerify?: boolean;
    readonly requireUserConfig?: boolean;
    readonly useEditor?: boolean;
    readonly verbose?: boolean;
    readonly postCommitCommand?: string;
}

export enum ForcePushMode {
    Force = 0,
    ForceWithLease = 1,
    ForceWithLeaseIfIncludes = 2,
}

// Helper type for event
export interface Event<T> {
    (listener: (e: T) => any, thisArgs?: any, disposables?: { dispose(): any }[]): { dispose(): any };
}

// Re-export vscode Uri type
export type Uri = import('vscode').Uri;
