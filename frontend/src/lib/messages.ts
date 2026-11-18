// Mensagens da API (inglês, estáveis) → português. Mensagem desconhecida vira texto genérico: nada técnico na tela.

const MESSAGES: Record<string, string> = {
  'Invalid email or password': 'E-mail ou senha incorretos.',
  'Too many failed attempts. Try again later': 'Conta bloqueada por muitas tentativas. Tente de novo mais tarde.',
  'Too many requests': 'Muitas tentativas seguidas. Aguarde um minuto.',
  'Email already in use': 'Este e-mail já tem cadastro.',
  'Password is too common. Choose a less predictable one': 'Senha muito comum. Escolha uma menos previsível.',
  'Password must not contain your name, your email or the app name': 'A senha não pode conter seu nome, seu e-mail ou o nome do app.',
  'Invalid password': 'Senha atual incorreta.',
  'New password must be different from the current one': 'A nova senha precisa ser diferente da atual.',
  'Authentication required': 'Sua sessão terminou. Entre de novo.',
  'Validation failed': 'Confira os campos destacados.',
  'Workspace not found': 'Espaço não encontrado.',
  'Only owners can do this': 'Só quem administra o espaço pode fazer isso.',
  'The last owner cannot leave the workspace': 'O espaço precisa de outra pessoa como dona antes de você sair.',
  'Invalid or expired invite': 'Convite inválido ou vencido. Peça um novo.',
  'Already a member': 'Você já participa deste espaço.',
  'Member not found': 'Pessoa não encontrada no espaço.',
  'Account not found': 'Conta não encontrada.',
  'Account is archived': 'Esta conta está arquivada.',
  'Account name already in use': 'Já existe uma conta com esse nome.',
  'Account has transactions; archive it instead': 'A conta tem lançamentos. Arquive em vez de excluir.',
  'Category not found': 'Categoria não encontrada.',
  'Category is archived': 'Esta categoria está arquivada.',
  'Category name already in use': 'Já existe uma categoria com esse nome.',
  'Category has transactions; archive it instead': 'A categoria tem lançamentos. Arquive em vez de excluir.',
  'Category is required': 'Escolha uma categoria.',
  'Category does not match the transaction type': 'A categoria não combina com o tipo do lançamento.',
  'Transfer needs a destination account': 'Escolha a conta de destino.',
  'Destination must be a different account': 'A conta de destino precisa ser outra.',
  'Date out of range': 'Data fora do intervalo permitido.',
  'Transaction not found': 'Lançamento não encontrado.',
  'Request body too large': 'Os dados enviados são grandes demais.',
  'Card not found': 'Cartão não encontrado.',
  'Card is archived': 'Este cartão está arquivado.',
  'Card name already in use': 'Já existe um cartão com esse nome.',
  'Card has purchases; archive it instead': 'O cartão tem compras. Arquive em vez de excluir.',
  'Purchase not found': 'Compra não encontrada.',
  'Each installment must be at least R$ 0,01': 'Cada parcela precisa ser de pelo menos R$ 0,01.',
  'Card payments are made through the card statement': 'Pague a fatura pela tela do cartão.',
  'Card payments are changed through the card statement': 'Pagamentos de fatura se alteram pela tela do cartão.',
}

export const GENERIC_ERROR = 'Algo deu errado. Tente de novo.'

export function translate(message: string | undefined): string {
  return (message && MESSAGES[message]) || GENERIC_ERROR
}
